package com.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 購物車項目資料傳輸物件
 */
@Schema(description = "購物車項目資訊")
public class CartItemDTO {
    
    @Schema(description = "購物車項目ID", example = "1")
    private Long id;
    
    @Schema(description = "產品ID", example = "1")
    private Long productId;
    
    @Schema(description = "產品名稱", example = "iPhone 15")
    private String productName;
    
    @Schema(description = "購買數量", example = "2")
    private Integer quantity;
    
    @Schema(description = "單價", example = "999.99")
    private BigDecimal unitPrice;
    
    @Schema(description = "小計金額", example = "1999.98")
    private BigDecimal totalPrice;
    
    @Schema(description = "創建時間", example = "2023-12-01T10:30:00")
    private LocalDateTime createdAt;
    
    // 預設建構子
    public CartItemDTO() {}
    
    // 建構子
    public CartItemDTO(Long id, Long productId, String productName, Integer quantity, 
                       BigDecimal unitPrice, BigDecimal totalPrice, LocalDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
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
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
    
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
    
    public BigDecimal getTotalPrice() {
        return totalPrice;
    }
    
    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}