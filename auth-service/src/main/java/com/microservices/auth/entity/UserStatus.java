package com.microservices.auth.entity;

/**
 * 用戶狀態枚舉
 */
public enum UserStatus {
    ACTIVE("啟用"),
    INACTIVE("停用"),
    LOCKED("鎖定"),
    EXPIRED("過期");
    
    private final String description;
    
    UserStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}