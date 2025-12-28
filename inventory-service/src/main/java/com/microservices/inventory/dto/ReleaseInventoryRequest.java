package com.microservices.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 庫存釋放請求 DTO
 * 用於釋放預留庫存的請求資料
 */
public class ReleaseInventoryRequest {
    
    @NotNull(message = "用戶ID不能為空")
    private Long userId;
    
    @NotNull(message = "釋放數量不能為空")
    @Min(value = 1, message = "釋放數量必須大於0")
    private Integer quantity;
    
    private String releaseType; // "TEMPORARY" 或 "CONFIRMED"
    
    // 預設建構子
    public ReleaseInventoryRequest() {}
    
    // 建構子
    public ReleaseInventoryRequest(Long userId, Integer quantity, String releaseType) {
        this.userId = userId;
        this.quantity = quantity;
        this.releaseType = releaseType;
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
    
    public String getReleaseType() {
        return releaseType;
    }
    
    public void setReleaseType(String releaseType) {
        this.releaseType = releaseType;
    }
}