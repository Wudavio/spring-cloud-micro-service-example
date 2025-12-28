package com.microservices.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 確認預留請求 DTO
 * 用於將臨時預留轉換為正式預留的請求資料
 */
public class ConfirmReservationRequest {
    
    @NotBlank(message = "客戶ID不能為空")
    private String customerId;
    
    @NotNull(message = "確認數量不能為空")
    @Min(value = 1, message = "確認數量必須大於0")
    private Integer quantity;
    
    // 預設建構子
    public ConfirmReservationRequest() {}
    
    // 建構子
    public ConfirmReservationRequest(String customerId, Integer quantity) {
        this.customerId = customerId;
        this.quantity = quantity;
    }
    
    // Getters and Setters
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}