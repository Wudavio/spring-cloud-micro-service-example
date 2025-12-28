package com.microservices.inventory.entity;

/**
 * 庫存預留類型枚舉
 * 定義庫存預留的兩種類型：臨時預留和確認預留
 */
public enum ReservationType {
    /**
     * 臨時預留 - 購物車階段的庫存預留，有時間限制
     */
    TEMPORARY("臨時預留"),
    
    /**
     * 確認預留 - 訂單確認後的庫存預留，直到訂單完成或取消
     */
    CONFIRMED("確認預留");
    
    private final String description;
    
    ReservationType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}