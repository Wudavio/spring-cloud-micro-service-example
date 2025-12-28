package com.microservices.order.exception;

/**
 * 購物車未找到異常
 */
public class CartNotFoundException extends RuntimeException {
    
    public CartNotFoundException(String message) {
        super(message);
    }
    
    public CartNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}