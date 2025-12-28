package com.microservices.auth.entity;

/**
 * 用戶角色枚舉
 */
public enum UserRole {
    CUSTOMER("客戶"),
    ADMIN("管理員"),
    OPERATOR("操作員");
    
    private final String description;
    
    UserRole(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}