package com.microservices.product.event;

import com.microservices.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public abstract class ProductEvent {
    private final Long productId;
    private final String productName;
    private final BigDecimal price;
    private final String category;
    private final ProductStatus status;
    private final LocalDateTime timestamp;
    private final String eventType;

    protected ProductEvent(Long productId, String productName, BigDecimal price, 
                          String category, ProductStatus status, String eventType) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.category = category;
        this.status = status;
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getPrice() { return price; }
    public String getCategory() { return category; }
    public ProductStatus getStatus() { return status; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }

    @Override
    public String toString() {
        return "ProductEvent{" +
                "productId=" + productId +
                ", productName='" + productName + '\'' +
                ", price=" + price +
                ", category='" + category + '\'' +
                ", status=" + status +
                ", timestamp=" + timestamp +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}