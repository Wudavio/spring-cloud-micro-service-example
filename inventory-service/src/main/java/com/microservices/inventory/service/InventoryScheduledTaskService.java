package com.microservices.inventory.service;

/**
 * 庫存定時任務服務介面
 * 負責處理庫存相關的定時任務，如過期預留清理和低庫存檢查
 */
public interface InventoryScheduledTaskService {

    /**
     * 清理過期的臨時預留
     * 定時執行，清理超過過期時間的臨時預留記錄
     */
    void cleanupExpiredReservations();

    /**
     * 檢查並發送低庫存告警
     * 定時執行，檢查所有產品的庫存水準並發送告警
     */
    void checkLowStockAlerts();

    /**
     * 執行庫存健康檢查
     * 定時執行，檢查庫存數據的一致性和完整性
     */
    void performInventoryHealthCheck();
}