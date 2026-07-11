package com.microservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * 庫存預留請求 DTO
 * 用於臨時預留庫存的請求資料
 */
public class ReserveInventoryRequest {
    
    @NotNull(message = "用戶ID不能為空")
    private Long userId;
    
    @NotNull(message = "預留數量不能為空")
    @Min(value = 1, message = "預留數量必須大於0")
    private Integer quantity;
    
    @Schema(type = "string", format = "date-time", example = "2026-07-11T14:30:00+08:00")
    private OffsetDateTime expiresAt;
    
    // 預設建構子
    public ReserveInventoryRequest() {}
    
    // 建構子
    public ReserveInventoryRequest(Long userId, Integer quantity, OffsetDateTime expiresAt) {
        this.userId = userId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
