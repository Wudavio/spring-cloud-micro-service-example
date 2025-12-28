package com.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 訂單項目資料傳輸物件
 */
@Schema(description = "訂單項目資訊")
public class OrderItemDTO {
    
    @Schema(description = "訂單項目ID", example = "1")
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
    
    // 預設建構子
    public OrderItemDTO() {}
    
    // 建構子
    public OrderItemDTO(Long id, Long productId, String productName, Integer quantity,
                        BigDecimal unitPrice, BigDecimal totalPrice) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
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
}