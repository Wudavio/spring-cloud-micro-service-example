package com.microservices.inventory.exception;

/**
 * 重試次數耗盡異常
 * 當重試達到最大次數仍然失敗時拋出此異常
 * 實現需求 10.4: 重試次數達到上限仍然失敗時，返回操作失敗並建議客戶稍後重試
 */
public class RetryExhaustedException extends RuntimeException {
    
    private final int maxAttempts;
    private final String operation;
    
    public RetryExhaustedException(String operation, int maxAttempts, Throwable cause) {
        super(String.format("操作 '%s' 重試 %d 次後仍然失敗，請稍後重試", operation, maxAttempts), cause);
        this.operation = operation;
        this.maxAttempts = maxAttempts;
    }
    
    public RetryExhaustedException(String operation, int maxAttempts) {
        super(String.format("操作 '%s' 重試 %d 次後仍然失敗，請稍後重試", operation, maxAttempts));
        this.operation = operation;
        this.maxAttempts = maxAttempts;
    }
    
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    public String getOperation() {
        return operation;
    }
}