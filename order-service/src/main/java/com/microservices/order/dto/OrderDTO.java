package com.microservices.order.dto;

import com.microservices.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 訂單資料傳輸物件
 */
@Schema(description = "訂單資訊")
public class OrderDTO {
    
    @Schema(description = "訂單ID", example = "1")
    private Long id;
    
    @Schema(description = "訂單號", example = "ORD-20231201-001")
    private String orderNumber;
    
    @Schema(description = "客戶ID", example = "customer123")
    private String customerId;
    
    @Schema(description = "訂單狀態", example = "PENDING")
    private OrderStatus status;
    
    @Schema(description = "訂單總金額", example = "299.99")
    private BigDecimal totalAmount;
    
    @Schema(description = "訂單項目列表")
    private List<OrderItemDTO> items;
    
    @Schema(description = "創建時間", example = "2023-12-01T10:30:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "最後更新時間", example = "2023-12-01T10:30:00")
    private LocalDateTime updatedAt;
    
    // 預設建構子
    public OrderDTO() {}
    
    // 建構子
    public OrderDTO(Long id, String orderNumber, String customerId, OrderStatus status,
                    BigDecimal totalAmount, List<OrderItemDTO> items, 
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getOrderNumber() {
        return orderNumber;
    }
    
    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public OrderStatus getStatus() {
        return status;
    }
    
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public List<OrderItemDTO> getItems() {
        return items;
    }
    
    public void setItems(List<OrderItemDTO> items) {
        this.items = items;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}