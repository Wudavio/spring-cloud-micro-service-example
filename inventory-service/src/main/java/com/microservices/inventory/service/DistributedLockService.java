package com.microservices.inventory.service;

import com.microservices.inventory.util.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 分散式鎖服務
 * 提供高層次的鎖管理和執行功能
 */
@Service
public class DistributedLockService {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockService.class);
    
    @Autowired
    private DistributedLock distributedLock;
    
    /**
     * 在分散式鎖保護下執行操作
     * 
     * @param lockKey 鎖的鍵值
     * @param operation 要執行的操作
     * @param <T> 操作返回值類型
     * @return 操作執行結果
     * @throws LockAcquisitionException 當無法獲取鎖時拋出
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> operation) throws LockAcquisitionException {
        return executeWithLock(lockKey, operation, 30L, 10L);
    }
    
    /**
     * 在分散式鎖保護下執行操作（指定超時時間）
     * 
     * @param lockKey 鎖的鍵值
     * @param operation 要執行的操作
     * @param lockTimeoutSeconds 鎖持有超時時間（秒）
     * @param acquireTimeoutSeconds 獲取鎖的超時時間（秒）
     * @param <T> 操作返回值類型
     * @return 操作執行結果
     * @throws LockAcquisitionException 當無法獲取鎖時拋出
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> operation, 
                                long lockTimeoutSeconds, long acquireTimeoutSeconds) 
                                throws LockAcquisitionException {
        
        DistributedLock.LockResult lockResult = distributedLock.tryLock(
            lockKey, lockTimeoutSeconds, acquireTimeoutSeconds);
        
        if (!lockResult.isAcquired()) {
            throw new LockAcquisitionException("無法獲取分散式鎖: " + lockKey);
        }
        
        try {
            logger.debug("開始執行鎖保護操作: {}", lockKey);
            return operation.get();
        } finally {
            boolean released = distributedLock.releaseLock(lockResult);
            if (!released) {
                logger.warn("釋放鎖失敗: {}", lockKey);
            }
        }
    }
    
    /**
     * 在分散式鎖保護下執行無返回值操作
     * 
     * @param lockKey 鎖的鍵值
     * @param operation 要執行的操作
     * @throws LockAcquisitionException 當無法獲取鎖時拋出
     */
    public void executeWithLock(String lockKey, Runnable operation) throws LockAcquisitionException {
        executeWithLock(lockKey, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * 嘗試在分散式鎖保護下執行操作，如果無法獲取鎖則返回預設值
     * 
     * @param lockKey 鎖的鍵值
     * @param operation 要執行的操作
     * @param defaultValue 無法獲取鎖時的預設返回值
     * @param <T> 操作返回值類型
     * @return 操作執行結果或預設值
     */
    public <T> T tryExecuteWithLock(String lockKey, Supplier<T> operation, T defaultValue) {
        try {
            return executeWithLock(lockKey, operation);
        } catch (LockAcquisitionException e) {
            logger.warn("無法獲取鎖，返回預設值: {}", lockKey);
            return defaultValue;
        }
    }
    
    /**
     * 檢查指定的鎖是否被持有
     * 
     * @param lockKey 鎖的鍵值
     * @return 鎖是否被持有
     */
    public boolean isLocked(String lockKey) {
        return distributedLock.isLocked(lockKey);
    }
    
    /**
     * 強制釋放指定的鎖（謹慎使用）
     * 
     * @param lockKey 鎖的鍵值
     * @return 是否成功釋放
     */
    public boolean forceReleaseLock(String lockKey) {
        return distributedLock.forceReleaseLock(lockKey);
    }
    
    /**
     * 鎖獲取異常
     */
    public static class LockAcquisitionException extends Exception {
        public LockAcquisitionException(String message) {
            super(message);
        }
        
        public LockAcquisitionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}