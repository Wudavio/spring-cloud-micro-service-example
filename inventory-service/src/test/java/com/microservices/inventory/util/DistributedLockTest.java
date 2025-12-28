package com.microservices.inventory.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分散式鎖基本功能測試
 */
@SpringBootTest
@ActiveProfiles("test")
class DistributedLockTest {

    @Autowired
    private DistributedLock distributedLock;

    @Test
    void testBasicLockAcquisitionAndRelease() {
        String lockKey = "test-lock-1";
        
        // 測試鎖獲取
        DistributedLock.LockResult lockResult = distributedLock.tryLock(lockKey);
        assertTrue(lockResult.isAcquired(), "應該能夠獲取鎖");
        assertNotNull(lockResult.getLockValue(), "鎖值不應該為空");
        
        // 測試鎖狀態檢查
        assertTrue(distributedLock.isLocked(lockKey), "鎖應該處於持有狀態");
        
        // 測試鎖釋放
        boolean released = distributedLock.releaseLock(lockResult);
        assertTrue(released, "應該能夠成功釋放鎖");
        
        // 驗證鎖已釋放
        assertFalse(distributedLock.isLocked(lockKey), "鎖應該已被釋放");
    }

    @Test
    void testLockMutualExclusion() {
        String lockKey = "test-lock-2";
        
        // 第一個鎖獲取
        DistributedLock.LockResult firstLock = distributedLock.tryLock(lockKey, 5, 1);
        assertTrue(firstLock.isAcquired(), "第一個鎖應該能夠獲取");
        
        // 第二個鎖嘗試獲取（應該失敗）
        DistributedLock.LockResult secondLock = distributedLock.tryLock(lockKey, 5, 1);
        assertFalse(secondLock.isAcquired(), "第二個鎖不應該能夠獲取");
        
        // 釋放第一個鎖
        distributedLock.releaseLock(firstLock);
        
        // 現在第二個鎖應該能夠獲取
        DistributedLock.LockResult thirdLock = distributedLock.tryLock(lockKey, 5, 1);
        assertTrue(thirdLock.isAcquired(), "釋放後應該能夠重新獲取鎖");
        
        // 清理
        distributedLock.releaseLock(thirdLock);
    }

    @Test
    void testLockTimeout() {
        String lockKey = "test-lock-3";
        
        // 獲取鎖但不釋放，等待超時
        DistributedLock.LockResult lockResult = distributedLock.tryLock(lockKey, 1, 1); // 1秒超時
        assertTrue(lockResult.isAcquired(), "應該能夠獲取鎖");
        
        // 等待鎖超時
        try {
            Thread.sleep(2000); // 等待2秒，超過鎖的1秒超時時間
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 現在應該能夠重新獲取鎖（因為原鎖已超時）
        DistributedLock.LockResult newLock = distributedLock.tryLock(lockKey, 5, 1);
        assertTrue(newLock.isAcquired(), "超時後應該能夠重新獲取鎖");
        
        // 清理
        distributedLock.releaseLock(newLock);
    }

    @Test
    void testForceReleaseLock() {
        String lockKey = "test-lock-4";
        
        // 獲取鎖
        DistributedLock.LockResult lockResult = distributedLock.tryLock(lockKey);
        assertTrue(lockResult.isAcquired(), "應該能夠獲取鎖");
        
        // 強制釋放鎖
        boolean forceReleased = distributedLock.forceReleaseLock(lockKey);
        assertTrue(forceReleased, "應該能夠強制釋放鎖");
        
        // 驗證鎖已釋放
        assertFalse(distributedLock.isLocked(lockKey), "鎖應該已被強制釋放");
    }
}