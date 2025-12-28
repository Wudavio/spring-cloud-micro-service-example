package com.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 更新購物車項目請求
 */
@Schema(description = "更新購物車項目的請求物件")
public class UpdateCartItemRequest {
    
    @Schema(description = "客戶ID", example = "customer123", required = true)
    @NotBlank(message = "客戶ID不能為空")
    private String customerId;
    
    @Schema(description = "新的購買數量", example = "3", minimum = "1", required = true)
    @NotNull(message = "數量不能為空")
    @Min(value = 1, message = "數量必須大於0")
    private Integer quantity;
    
    // 預設建構子
    public UpdateCartItemRequest() {}
    
    // 建構子
    public UpdateCartItemRequest(String customerId, Integer quantity) {
        this.customerId = customerId;
        this.quantity = quantity;
    }
    
    // Getters and Setters
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}