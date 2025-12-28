package com.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 下單請求
 */
@Schema(description = "下單請求物件")
public class PlaceOrderRequest {
    
    @Schema(description = "客戶ID", example = "customer123", required = true)
    @NotBlank(message = "客戶ID不能為空")
    private String customerId;
    
    // 預設建構子
    public PlaceOrderRequest() {}
    
    // 建構子
    public PlaceOrderRequest(String customerId) {
        this.customerId = customerId;
    }
    
    // Getters and Setters
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}