package com.microservices.product.listener;

import com.microservices.product.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ProductEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductEventListener.class);

    @EventListener
    @Async
    public void handleProductCreatedEvent(ProductCreatedEvent event) {
        logger.info("Handling ProductCreatedEvent: Product {} (ID: {}) was created", 
                   event.getProductName(), event.getProductId());
        
        // Here you could:
        // - Send notifications to inventory service
        // - Update search indexes
        // - Send notifications to other services
        // - Log audit information
    }

    @EventListener
    @Async
    public void handleProductUpdatedEvent(ProductUpdatedEvent event) {
        logger.info("Handling ProductUpdatedEvent: Product {} (ID: {}) was updated", 
                   event.getProductName(), event.getProductId());
        
        // Here you could:
        // - Notify inventory service of price changes
        // - Update cached product information
        // - Send notifications to subscribers
    }

    @EventListener
    @Async
    public void handleProductDeletedEvent(ProductDeletedEvent event) {
        logger.info("Handling ProductDeletedEvent: Product {} (ID: {}) was deleted", 
                   event.getProductName(), event.getProductId());
        
        // Here you could:
        // - Notify inventory service to clean up inventory records
        // - Remove from search indexes
        // - Cancel any pending orders for this product
    }

    @EventListener
    @Async
    public void handleProductStatusChangedEvent(ProductStatusChangedEvent event) {
        logger.info("Handling ProductStatusChangedEvent: Product {} (ID: {}) status changed from {} to {}", 
                   event.getProductName(), event.getProductId(), 
                   event.getPreviousStatus(), event.getStatus());
        
        // Here you could:
        // - Notify inventory service of status changes
        // - Update product availability in other services
        // - Send notifications to customers about product availability
    }
}