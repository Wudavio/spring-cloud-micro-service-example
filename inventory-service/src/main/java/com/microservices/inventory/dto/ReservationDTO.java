package com.microservices.inventory.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.microservices.inventory.entity.ReservationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 庫存預留記錄 DTO
 * 用於 API 回應的預留資料傳輸物件
 */
public class ReservationDTO {
    
    private Long id;
    
    @NotNull(message = "產品ID不能為空")
    private Long productId;
    
    @NotBlank(message = "客戶ID不能為空")
    private String customerId;
    
    @NotNull(message = "預留數量不能為空")
    @Min(value = 1, message = "預留數量必須大於0")
    private Integer quantity;
    
    @NotNull(message = "預留類型不能為空")
    private ReservationType type;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    private Boolean isExpired;
    
    // 預設建構子
    public ReservationDTO() {}
    
    // 建構子
    public ReservationDTO(Long id, Long productId, String customerId, Integer quantity,
                         ReservationType type, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.customerId = customerId;
        this.quantity = quantity;
        this.type = type;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.isExpired = LocalDateTime.now().isAfter(expiresAt);
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getProductId() {
        return productId;
    }
    
    public void setProductId(Long productId) {
        this.productId = productId;
    }
    
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
    
    public ReservationType getType() {
        return type;
    }
    
    public void setType(ReservationType type) {
        this.type = type;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Boolean getIsExpired() {
        return isExpired;
    }
    
    public void setIsExpired(Boolean isExpired) {
        this.isExpired = isExpired;
    }
}