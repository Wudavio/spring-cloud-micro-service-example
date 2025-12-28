package com.microservices.product.event;

import com.microservices.product.entity.ProductStatus;

import java.math.BigDecimal;

public class ProductUpdatedEvent extends ProductEvent {
    private final ProductStatus previousStatus;
    private final BigDecimal previousPrice;

    public ProductUpdatedEvent(Long productId, String productName, BigDecimal price, 
                              String category, ProductStatus status,
                              ProductStatus previousStatus, BigDecimal previousPrice) {
        super(productId, productName, price, category, status, "PRODUCT_UPDATED");
        this.previousStatus = previousStatus;
        this.previousPrice = previousPrice;
    }

    public ProductStatus getPreviousStatus() { return previousStatus; }
    public BigDecimal getPreviousPrice() { return previousPrice; }
}