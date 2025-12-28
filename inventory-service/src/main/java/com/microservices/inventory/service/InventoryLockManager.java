package com.microservices.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 庫存鎖管理器
 * 專門用於庫存操作的分散式鎖管理
 */
@Component
public class InventoryLockManager {

    private static final Logger logger = LoggerFactory.getLogger(InventoryLockManager.class);
    
    private static final String INVENTORY_LOCK_PREFIX = "product:";
    private static final long INVENTORY_LOCK_TIMEOUT_SECONDS = 30L;
    private static final long INVENTORY_ACQUIRE_TIMEOUT_SECONDS = 10L;
    
    @Autowired
    private DistributedLockService distributedLockService;
    
    /**
     * 在產品庫存鎖保護下執行操作
     * 
     * @param productId 產品ID
     * @param operation 要執行的操作
     * @param <T> 操作返回值類型
     * @return 操作執行結果
     * @throws DistributedLockService.LockAcquisitionException 當無法獲取鎖時拋出
     */
    public <T> T executeWithProductLock(Long productId, Supplier<T> operation) 
            throws DistributedLockService.LockAcquisitionException {
        
        String lockKey = generateProductLockKey(productId);
        logger.debug("嘗試獲取產品庫存鎖: productId={}", productId);
        
        return distributedLockService.executeWithLock(
            lockKey, 
            operation, 
            INVENTORY_LOCK_TIMEOUT_SECONDS, 
            INVENTORY_ACQUIRE_TIMEOUT_SECONDS
        );
    }
    
    /**
     * 在產品庫存鎖保護下執行無返回值操作
     * 
     * @param productId 產品ID
     * @param operation 要執行的操作
     * @throws DistributedLockService.LockAcquisitionException 當無法獲取鎖時拋出
     */
    public void executeWithProductLock(Long productId, Runnable operation) 
            throws DistributedLockService.LockAcquisitionException {
        
        executeWithProductLock(productId, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * 嘗試在產品庫存鎖保護下執行操作，如果無法獲取鎖則返回預設值
     * 
     * @param productId 產品ID
     * @param operation 要執行的操作
     * @param defaultValue 無法獲取鎖時的預設返回值
     * @param <T> 操作返回值類型
     * @return 操作執行結果或預設值
     */
    public <T> T tryExecuteWithProductLock(Long productId, Supplier<T> operation, T defaultValue) {
        String lockKey = generateProductLockKey(productId);
        return distributedLockService.tryExecuteWithLock(lockKey, operation, defaultValue);
    }
    
    /**
     * 檢查產品庫存鎖是否被持有
     * 
     * @param productId 產品ID
     * @return 鎖是否被持有
     */
    public boolean isProductLocked(Long productId) {
        String lockKey = generateProductLockKey(productId);
        return distributedLockService.isLocked(lockKey);
    }
    
    /**
     * 強制釋放產品庫存鎖（謹慎使用）
     * 
     * @param productId 產品ID
     * @return 是否成功釋放
     */
    public boolean forceReleaseProductLock(Long productId) {
        String lockKey = generateProductLockKey(productId);
        logger.warn("強制釋放產品庫存鎖: productId={}", productId);
        return distributedLockService.forceReleaseLock(lockKey);
    }
    
    /**
     * 生成產品鎖鍵值
     * 
     * @param productId 產品ID
     * @return 鎖鍵值
     */
    private String generateProductLockKey(Long productId) {
        return INVENTORY_LOCK_PREFIX + productId;
    }
    
    /**
     * 批量檢查多個產品的鎖狀態
     * 
     * @param productIds 產品ID陣列
     * @return 是否有任何產品被鎖定
     */
    public boolean isAnyProductLocked(Long... productIds) {
        for (Long productId : productIds) {
            if (isProductLocked(productId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 獲取鎖的配置資訊
     * 
     * @return 鎖配置描述
     */
    public String getLockConfiguration() {
        return String.format("庫存鎖配置 - 持有超時: %d秒, 獲取超時: %d秒", 
            INVENTORY_LOCK_TIMEOUT_SECONDS, INVENTORY_ACQUIRE_TIMEOUT_SECONDS);
    }
}