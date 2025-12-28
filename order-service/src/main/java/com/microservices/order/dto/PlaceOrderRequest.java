package com.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 下單請求
 */
@Schema(description = "下單請求物件")
public class PlaceOrderRequest {
    
    // 用戶ID將從 JWT token 中獲取，不需要在請求中提供
    private Long userId;
    
    // 預設建構子
    public PlaceOrderRequest() {}
    
    // 建構子
    public PlaceOrderRequest(Long userId) {
        this.userId = userId;
    }
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
}