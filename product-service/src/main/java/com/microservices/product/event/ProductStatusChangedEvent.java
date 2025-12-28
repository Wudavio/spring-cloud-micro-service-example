package com.microservices.product.event;

import com.microservices.product.entity.ProductStatus;

import java.math.BigDecimal;

public class ProductStatusChangedEvent extends ProductEvent {
    private final ProductStatus previousStatus;

    public ProductStatusChangedEvent(Long productId, String productName, BigDecimal price, 
                                   String category, ProductStatus newStatus, ProductStatus previousStatus) {
        super(productId, productName, price, category, newStatus, "PRODUCT_STATUS_CHANGED");
        this.previousStatus = previousStatus;
    }

    public ProductStatus getPreviousStatus() { return previousStatus; }
}