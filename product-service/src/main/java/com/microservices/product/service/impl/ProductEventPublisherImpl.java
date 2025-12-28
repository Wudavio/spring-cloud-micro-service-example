package com.microservices.product.service.impl;

import com.microservices.product.event.ProductEvent;
import com.microservices.product.service.ProductEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ProductEventPublisherImpl implements ProductEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductEventPublisherImpl.class);
    
    private final ApplicationEventPublisher applicationEventPublisher;

    public ProductEventPublisherImpl(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishEvent(ProductEvent event) {
        try {
            logger.info("Publishing product event: {}", event);
            applicationEventPublisher.publishEvent(event);
            logger.debug("Successfully published product event: {}", event.getEventType());
        } catch (Exception e) {
            logger.error("Failed to publish product event: {}", event, e);
            // In a production system, you might want to implement retry logic or dead letter queue
        }
    }
}