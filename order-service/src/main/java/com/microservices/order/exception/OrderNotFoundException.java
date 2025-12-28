package com.microservices.order.exception;

/**
 * 訂單未找到異常
 */
public class OrderNotFoundException extends RuntimeException {
    
    public OrderNotFoundException(String message) {
        super(message);
    }
    
    public OrderNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}