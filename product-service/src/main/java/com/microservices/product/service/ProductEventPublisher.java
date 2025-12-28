package com.microservices.product.service;

import com.microservices.product.event.ProductEvent;

public interface ProductEventPublisher {
    void publishEvent(ProductEvent event);
}