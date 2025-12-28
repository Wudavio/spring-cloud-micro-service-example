package com.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotNull;

/**
 * 更新購物車項目請求
 */
@Schema(description = "更新購物車項目的請求物件")
public class UpdateCartItemRequest {
    
    @Schema(description = "用戶ID", example = "123")
    @NotNull(message = "用戶ID不能為空")
    private Long userId;
    
    @Schema(description = "新的購買數量", example = "3", minimum = "1")
    @NotNull(message = "數量不能為空")
    @Min(value = 1, message = "數量必須大於0")
    private Integer quantity;
    
    // 預設建構子
    public UpdateCartItemRequest() {}
    
    // 建構子
    public UpdateCartItemRequest(Long userId, Integer quantity) {
        this.userId = userId;
        this.quantity = quantity;
    }
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}