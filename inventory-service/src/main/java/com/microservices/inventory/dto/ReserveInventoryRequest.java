package com.microservices.inventory.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 庫存預留請求 DTO
 * 用於臨時預留庫存的請求資料
 */
public class ReserveInventoryRequest {
    
    @NotBlank(message = "客戶ID不能為空")
    private String customerId;
    
    @NotNull(message = "預留數量不能為空")
    @Min(value = 1, message = "預留數量必須大於0")
    private Integer quantity;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
    
    // 預設建構子
    public ReserveInventoryRequest() {}
    
    // 建構子
    public ReserveInventoryRequest(String customerId, Integer quantity, LocalDateTime expiresAt) {
        this.customerId = customerId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
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
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}