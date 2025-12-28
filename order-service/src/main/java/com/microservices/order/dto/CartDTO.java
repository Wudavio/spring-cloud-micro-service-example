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
    
    @Schema(description = "客戶ID", example = "customer123")
    private String customerId;
    
    @Schema(description = "購物車項目列表")
    private List<CartItemDTO> items;
    
    @Schema(description = "購物車總金額", example = "299.99")
    private BigDecimal totalAmount;
    
    @Schema(description = "最後更新時間", example = "2023-12-01T10:30:00")
    private LocalDateTime updatedAt;
    
    // 預設建構子
    public CartDTO() {}
    
    // 建構子
    public CartDTO(Long id, String customerId, List<CartItemDTO> items, BigDecimal totalAmount, LocalDateTime updatedAt) {
        this.id = id;
        this.customerId = customerId;
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
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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