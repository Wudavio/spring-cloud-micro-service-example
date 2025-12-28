package com.microservices.order.entity;

/**
 * 訂單狀態枚舉
 */
public enum OrderStatus {
    /**
     * 待處理
     */
    PENDING,
    
    /**
     * 已確認
     */
    CONFIRMED,
    
    /**
     * 已出貨
     */
    SHIPPED,
    
    /**
     * 已送達
     */
    DELIVERED,
    
    /**
     * 已取消
     */
    CANCELLED
}