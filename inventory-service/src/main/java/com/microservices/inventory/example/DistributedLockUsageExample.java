package com.microservices.inventory.example;

import com.microservices.inventory.service.DistributedLockService;
import com.microservices.inventory.service.InventoryLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分散式鎖使用範例
 * 展示如何在庫存操作中使用分散式鎖
 */
@Component
public class DistributedLockUsageExample {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockUsageExample.class);
    
    @Autowired
    private DistributedLockService distributedLockService;
    
    @Autowired
    private InventoryLockManager inventoryLockManager;
    
    /**
     * 範例：使用分散式鎖保護庫存更新操作
     */
    public void updateInventoryWithLock(Long productId, int quantity) {
        try {
            inventoryLockManager.executeWithProductLock(productId, () -> {
                logger.info("開始更新產品 {} 的庫存，數量: {}", productId, quantity);
                
                // 模擬庫存更新邏輯
                // 1. 檢查當前庫存
                // 2. 驗證庫存是否足夠
                // 3. 更新庫存數量
                // 4. 記錄操作日誌
                
                simulateInventoryUpdate(productId, quantity);
                
                logger.info("成功更新產品 {} 的庫存", productId);
            });
        } catch (DistributedLockService.LockAcquisitionException e) {
            logger.error("無法獲取產品 {} 的庫存鎖，操作失敗: {}", productId, e.getMessage());
            throw new RuntimeException("庫存更新失敗：系統繁忙，請稍後重試", e);
        }
    }
    
    /**
     * 範例：嘗試更新庫存，如果無法獲取鎖則返回預設結果
     */
    public boolean tryUpdateInventoryWithLock(Long productId, int quantity) {
        return inventoryLockManager.tryExecuteWithProductLock(productId, () -> {
            logger.info("嘗試更新產品 {} 的庫存，數量: {}", productId, quantity);
            simulateInventoryUpdate(productId, quantity);
            return true;
        }, false); // 如果無法獲取鎖，返回 false
    }
    
    /**
     * 範例：批量庫存操作
     */
    public void batchUpdateInventory(Long[] productIds, int[] quantities) {
        if (productIds.length != quantities.length) {
            throw new IllegalArgumentException("產品ID和數量陣列長度不匹配");
        }
        
        for (int i = 0; i < productIds.length; i++) {
            final Long productId = productIds[i];
            final int quantity = quantities[i];
            
            try {
                distributedLockService.executeWithLock(
                    "batch-update:" + productId,
                    () -> {
                        simulateInventoryUpdate(productId, quantity);
                        return null;
                    },
                    60L, // 批量操作使用較長的鎖超時時間
                    15L  // 較長的獲取超時時間
                );
            } catch (DistributedLockService.LockAcquisitionException e) {
                logger.warn("批量更新中產品 {} 獲取鎖失敗，跳過此產品: {}", productId, e.getMessage());
            }
        }
    }
    
    /**
     * 範例：檢查產品是否被鎖定
     */
    public boolean isProductBeingUpdated(Long productId) {
        return inventoryLockManager.isProductLocked(productId);
    }
    
    /**
     * 範例：緊急情況下強制釋放鎖
     */
    public boolean emergencyReleaseLock(Long productId) {
        logger.warn("緊急釋放產品 {} 的庫存鎖", productId);
        return inventoryLockManager.forceReleaseProductLock(productId);
    }
    
    /**
     * 模擬庫存更新操作
     */
    private void simulateInventoryUpdate(Long productId, int quantity) {
        try {
            // 模擬資料庫操作延遲
            Thread.sleep(100);
            
            logger.debug("模擬更新產品 {} 庫存，變更數量: {}", productId, quantity);
            
            // 這裡應該是實際的庫存更新邏輯
            // - 查詢當前庫存
            // - 驗證業務規則
            // - 更新庫存數量
            // - 記錄操作日誌
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("庫存更新操作被中斷", e);
        }
    }
    
    /**
     * 獲取鎖配置資訊
     */
    public String getLockInfo() {
        return inventoryLockManager.getLockConfiguration();
    }
}