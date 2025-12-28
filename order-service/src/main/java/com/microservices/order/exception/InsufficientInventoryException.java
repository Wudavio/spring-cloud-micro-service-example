package com.microservices.order.exception;

/**
 * 庫存不足異常
 */
public class InsufficientInventoryException extends RuntimeException {
    
    public InsufficientInventoryException(String message) {
        super(message);
    }
    
    public InsufficientInventoryException(String message, Throwable cause) {
        super(message, cause);
    }
}