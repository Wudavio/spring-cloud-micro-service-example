package com.microservices.inventory.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分散式鎖工具類
 * 提供分散式鎖的獲取、釋放和超時處理功能
 */
@Component
public class DistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLock.class);
    
    private static final String LOCK_PREFIX = "inventory:lock:";
    private static final long DEFAULT_LOCK_TIMEOUT_SECONDS = 30L;
    private static final long DEFAULT_ACQUIRE_TIMEOUT_SECONDS = 10L;
    
    /**
     * 使用字串序列化的 template，避免 JSON 序列化與 transaction 佇列問題導致鎖失效
     */
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("lockRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;
    
    // Lua 腳本用於原子性釋放鎖
    private static final String UNLOCK_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "    return redis.call('del', KEYS[1]) " +
        "else " +
        "    return 0 " +
        "end";
    
    private final DefaultRedisScript<Long> unlockScript;
    
    public DistributedLock() {
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setScriptText(UNLOCK_SCRIPT);
        this.unlockScript.setResultType(Long.class);
    }
    
    /**
     * 嘗試獲取分散式鎖
     * 
     * @param lockKey 鎖的鍵值
     * @return LockResult 包含鎖獲取結果和鎖標識符
     */
    public LockResult tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_LOCK_TIMEOUT_SECONDS, DEFAULT_ACQUIRE_TIMEOUT_SECONDS);
    }
    
    /**
     * 嘗試獲取分散式鎖（指定超時時間）
     * 
     * @param lockKey 鎖的鍵值
     * @param lockTimeoutSeconds 鎖持有超時時間（秒）
     * @param acquireTimeoutSeconds 獲取鎖的超時時間（秒）
     * @return LockResult 包含鎖獲取結果和鎖標識符
     */
    public LockResult tryLock(String lockKey, long lockTimeoutSeconds, long acquireTimeoutSeconds) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        String lockValue = generateLockValue();
        
        long startTime = System.currentTimeMillis();
        long acquireTimeoutMillis = acquireTimeoutSeconds * 1000;
        
        try {
            while (System.currentTimeMillis() - startTime < acquireTimeoutMillis) {
                // 使用 SET NX EX 命令原子性地設置鎖
                Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(fullLockKey, lockValue, Duration.ofSeconds(lockTimeoutSeconds));
                
                if (Boolean.TRUE.equals(acquired)) {
                    logger.debug("成功獲取分散式鎖: {}", lockKey);
                    return new LockResult(true, lockValue, fullLockKey);
                }
                
                // 短暫等待後重試
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("獲取鎖時被中斷: {}", lockKey);
                    return new LockResult(false, null, fullLockKey);
                }
            }
            
            logger.warn("獲取分散式鎖超時: {}", lockKey);
            return new LockResult(false, null, fullLockKey);
            
        } catch (Exception e) {
            logger.error("獲取分散式鎖時發生錯誤: {}", lockKey, e);
            return new LockResult(false, null, fullLockKey);
        }
    }
    
    /**
     * 釋放分散式鎖
     * 
     * @param lockResult 鎖獲取時返回的結果
     * @return 是否成功釋放鎖
     */
    public boolean releaseLock(LockResult lockResult) {
        if (lockResult == null || !lockResult.isAcquired() || lockResult.getLockValue() == null) {
            return false;
        }
        
        try {
            // 使用 Lua 腳本原子性地檢查並釋放鎖
            Long result = redisTemplate.execute(
                unlockScript,
                Collections.singletonList(lockResult.getFullLockKey()),
                lockResult.getLockValue()
            );
            
            boolean released = result != null && result == 1L;
            if (released) {
                logger.debug("成功釋放分散式鎖: {}", lockResult.getFullLockKey());
            } else {
                logger.warn("釋放分散式鎖失敗，鎖可能已過期或被其他程序釋放: {}", lockResult.getFullLockKey());
            }
            
            return released;
            
        } catch (Exception e) {
            logger.error("釋放分散式鎖時發生錯誤: {}", lockResult.getFullLockKey(), e);
            return false;
        }
    }
    
    /**
     * 檢查鎖是否存在
     * 
     * @param lockKey 鎖的鍵值
     * @return 鎖是否存在
     */
    public boolean isLocked(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(fullLockKey));
        } catch (Exception e) {
            logger.error("檢查鎖狀態時發生錯誤: {}", lockKey, e);
            return false;
        }
    }
    
    /**
     * 強制釋放鎖（謹慎使用）
     * 
     * @param lockKey 鎖的鍵值
     * @return 是否成功釋放
     */
    public boolean forceReleaseLock(String lockKey) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        try {
            Boolean deleted = redisTemplate.delete(fullLockKey);
            logger.warn("強制釋放分散式鎖: {}, 結果: {}", lockKey, deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            logger.error("強制釋放鎖時發生錯誤: {}", lockKey, e);
            return false;
        }
    }
    
    /**
     * 生成唯一的鎖值
     * 
     * @return 唯一鎖值
     */
    private String generateLockValue() {
        return UUID.randomUUID().toString() + ":" + System.currentTimeMillis();
    }
    
    /**
     * 鎖結果類別
     */
    public static class LockResult {
        private final boolean acquired;
        private final String lockValue;
        private final String fullLockKey;
        
        public LockResult(boolean acquired, String lockValue, String fullLockKey) {
            this.acquired = acquired;
            this.lockValue = lockValue;
            this.fullLockKey = fullLockKey;
        }
        
        public boolean isAcquired() {
            return acquired;
        }
        
        public String getLockValue() {
            return lockValue;
        }
        
        public String getFullLockKey() {
            return fullLockKey;
        }
        
        @Override
        public String toString() {
            return "LockResult{" +
                    "acquired=" + acquired +
                    ", lockValue='" + lockValue + '\'' +
                    ", fullLockKey='" + fullLockKey + '\'' +
                    '}';
        }
    }
}