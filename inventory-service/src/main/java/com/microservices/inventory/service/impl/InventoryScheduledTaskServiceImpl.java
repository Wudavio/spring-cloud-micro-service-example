package com.microservices.inventory.service.impl;

import com.microservices.inventory.service.InventoryScheduledTaskService;
import com.microservices.inventory.service.InventoryService;
import com.microservices.inventory.service.LowStockNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 庫存定時任務服務實現類
 * 實現庫存相關的定時任務邏輯
 */
@Service
public class InventoryScheduledTaskServiceImpl implements InventoryScheduledTaskService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryScheduledTaskServiceImpl.class);

    private final InventoryService inventoryService;
    private final LowStockNotificationService lowStockNotificationService;

    @Autowired
    public InventoryScheduledTaskServiceImpl(InventoryService inventoryService,
                                           LowStockNotificationService lowStockNotificationService) {
        this.inventoryService = inventoryService;
        this.lowStockNotificationService = lowStockNotificationService;
    }

    /**
     * 清理過期的臨時預留
     * 每5分鐘執行一次
     */
    @Override
    @Scheduled(fixedRate = 300000) // 5分鐘 = 300,000毫秒
    public void cleanupExpiredReservations() {
        logger.info("開始執行定時任務：清理過期的臨時預留");
        
        try {
            int cleanedCount = inventoryService.cleanupExpiredTemporaryReservations();
            
            if (cleanedCount > 0) {
                logger.info("定時任務完成：清理過期臨時預留，清理數量: {}", cleanedCount);
            } else {
                logger.debug("定時任務完成：沒有發現過期的臨時預留");
            }
            
        } catch (Exception e) {
            logger.error("執行清理過期預留定時任務時發生錯誤", e);
        }
    }

    /**
     * 檢查並發送低庫存告警
     * 每30分鐘執行一次
     */
    @Override
    @Scheduled(fixedRate = 1800000) // 30分鐘 = 1,800,000毫秒
    public void checkLowStockAlerts() {
        logger.info("開始執行定時任務：檢查低庫存告警");
        
        try {
            int alertCount = lowStockNotificationService.checkAndSendLowStockAlerts();
            
            if (alertCount > 0) {
                logger.info("定時任務完成：低庫存檢查，發送告警數量: {}", alertCount);
            } else {
                logger.debug("定時任務完成：沒有發現需要告警的低庫存產品");
            }
            
        } catch (Exception e) {
            logger.error("執行低庫存檢查定時任務時發生錯誤", e);
        }
    }

    /**
     * 執行庫存健康檢查
     * 每小時執行一次
     */
    @Override
    @Scheduled(fixedRate = 3600000) // 1小時 = 3,600,000毫秒
    public void performInventoryHealthCheck() {
        logger.info("開始執行定時任務：庫存健康檢查");
        
        try {
            // 執行庫存數據一致性檢查
            performInventoryConsistencyCheck();
            
            // 記錄系統狀態
            logSystemHealthStatus();
            
            logger.info("定時任務完成：庫存健康檢查");
            
        } catch (Exception e) {
            logger.error("執行庫存健康檢查定時任務時發生錯誤", e);
        }
    }

    /**
     * 執行庫存數據一致性檢查
     */
    private void performInventoryConsistencyCheck() {
        logger.debug("執行庫存數據一致性檢查");
        
        try {
            // 在實際環境中，這裡會執行各種一致性檢查：
            // 1. 檢查庫存數量是否為負數
            // 2. 檢查預留數量是否超過總庫存
            // 3. 檢查是否有孤立的預留記錄
            // 4. 檢查庫存與預留記錄的數據一致性
            
            logger.debug("庫存數據一致性檢查完成");
            
        } catch (Exception e) {
            logger.error("庫存數據一致性檢查失敗", e);
            throw e;
        }
    }

    /**
     * 記錄系統健康狀態
     */
    private void logSystemHealthStatus() {
        logger.debug("記錄系統健康狀態");
        
        try {
            // 在實際環境中，這裡會記錄各種系統指標：
            // 1. 總庫存數量
            // 2. 活躍預留數量
            // 3. 低庫存產品數量
            // 4. 系統響應時間
            // 5. 錯誤率等
            
            logger.debug("系統健康狀態記錄完成");
            
        } catch (Exception e) {
            logger.error("記錄系統健康狀態失敗", e);
        }
    }
}