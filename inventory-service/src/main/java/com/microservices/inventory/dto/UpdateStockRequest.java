package com.microservices.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 更新庫存請求 DTO
 * 用於更新產品庫存數量的請求資料
 */
public class UpdateStockRequest {
    
    @NotNull(message = "新庫存數量不能為空")
    @Min(value = 0, message = "新庫存數量不能為負數")
    private Integer newStock;
    
    // 預設建構子
    public UpdateStockRequest() {}
    
    // 建構子
    public UpdateStockRequest(Integer newStock) {
        this.newStock = newStock;
    }
    
    // Getters and Setters
    public Integer getNewStock() {
        return newStock;
    }
    
    public void setNewStock(Integer newStock) {
        this.newStock = newStock;
    }
}