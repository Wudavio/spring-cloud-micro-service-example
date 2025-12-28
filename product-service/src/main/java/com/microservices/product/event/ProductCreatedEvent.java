package com.microservices.product.event;

import com.microservices.product.entity.ProductStatus;

import java.math.BigDecimal;

public class ProductCreatedEvent extends ProductEvent {
    
    public ProductCreatedEvent(Long productId, String productName, BigDecimal price, 
                              String category, ProductStatus status) {
        super(productId, productName, price, category, status, "PRODUCT_CREATED");
    }
}