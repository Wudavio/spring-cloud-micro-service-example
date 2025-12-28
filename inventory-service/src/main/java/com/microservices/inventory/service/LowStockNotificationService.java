package com.microservices.inventory.service;

import com.microservices.inventory.entity.Inventory;

import java.util.List;

/**
 * 低庫存通知服務介面
 * 負責處理低庫存告警和通知機制
 */
public interface LowStockNotificationService {

    /**
     * 檢查並發送低庫存告警
     * 
     * @return 發送告警的產品數量
     */
    int checkAndSendLowStockAlerts();

    /**
     * 為特定產品發送低庫存告警
     * 
     * @param inventory 庫存記錄
     */
    void sendLowStockAlert(Inventory inventory);

    /**
     * 批量發送低庫存告警
     * 
     * @param lowStockInventories 低庫存產品列表
     * @return 成功發送告警的數量
     */
    int sendBatchLowStockAlerts(List<Inventory> lowStockInventories);

    /**
     * 檢查產品是否需要發送低庫存告警
     * 
     * @param inventory 庫存記錄
     * @return 是否需要發送告警
     */
    boolean shouldSendLowStockAlert(Inventory inventory);
}