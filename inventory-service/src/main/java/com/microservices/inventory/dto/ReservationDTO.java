package com.microservices.inventory.dto;

import com.microservices.inventory.entity.ReservationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 庫存預留記錄 DTO
 * 用於 API 回應的預留資料傳輸物件
 */
public class ReservationDTO {
    
    private Long id;
    
    @NotNull(message = "產品ID不能為空")
    private Long productId;
    
    @NotNull(message = "用戶ID不能為空")
    private Long userId;
    
    @NotNull(message = "預留數量不能為空")
    @Min(value = 1, message = "預留數量必須大於0")
    private Integer quantity;
    
    @NotNull(message = "預留類型不能為空")
    @Schema(allowableValues = {"TEMPORARY", "CONFIRMED"})
    private ReservationType type;

    @Schema(type = "string", format = "date-time", example = "2026-07-11T06:30:00Z")
    private OffsetDateTime expiresAt;

    @Schema(type = "string", format = "date-time", example = "2026-07-11T06:00:00Z")
    private OffsetDateTime createdAt;
    
    private Boolean isExpired;
    
    // 預設建構子
    public ReservationDTO() {}
    
    // 建構子
    public ReservationDTO(Long id, Long productId, Long userId, Integer quantity,
                         ReservationType type, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.userId = userId;
        this.quantity = quantity;
        this.type = type;
        this.expiresAt = expiresAt.atOffset(ZoneOffset.UTC);
        this.createdAt = createdAt.atOffset(ZoneOffset.UTC);
        this.isExpired = OffsetDateTime.now(ZoneOffset.UTC).isAfter(this.expiresAt);
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
    
    public ReservationType getType() {
        return type;
    }
    
    public void setType(ReservationType type) {
        this.type = type;
    }
    
    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Boolean getIsExpired() {
        return isExpired;
    }
    
    public void setIsExpired(Boolean isExpired) {
        this.isExpired = isExpired;
    }
}
