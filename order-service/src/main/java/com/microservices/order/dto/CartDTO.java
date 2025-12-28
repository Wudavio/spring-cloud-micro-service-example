package com.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 購物車資料傳輸物件
 */
@Schema(description = "購物車資訊")
public class CartDTO {
    
    @Schema(description = "購物車ID", example = "1")
    private Long id;
    
    @Schema(description = "用戶ID", example = "123")
    private Long userId;
    
    @Schema(description = "購物車項目列表")
    private List<CartItemDTO> items;
    
    @Schema(description = "購物車總金額", example = "299.99")
    private BigDecimal totalAmount;
    
    @Schema(description = "最後更新時間", example = "2023-12-01T10:30:00")
    private LocalDateTime updatedAt;
    
    // 預設建構子
    public CartDTO() {}
    
    // 建構子
    public CartDTO(Long id, Long userId, List<CartItemDTO> items, BigDecimal totalAmount, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public List<CartItemDTO> getItems() {
        return items;
    }
    
    public void setItems(List<CartItemDTO> items) {
        this.items = items;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}