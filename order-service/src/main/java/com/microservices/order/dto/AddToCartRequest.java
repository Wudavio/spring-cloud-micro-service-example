package com.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 添加到購物車請求
 */
@Schema(description = "添加商品到購物車的請求物件")
public class AddToCartRequest {
    
    @Schema(description = "客戶ID", example = "customer123", required = true)
    @NotBlank(message = "客戶ID不能為空")
    private String customerId;
    
    @Schema(description = "產品ID", example = "1", required = true)
    @NotNull(message = "產品ID不能為空")
    private Long productId;
    
    @Schema(description = "購買數量", example = "2", minimum = "1", required = true)
    @NotNull(message = "數量不能為空")
    @Min(value = 1, message = "數量必須大於0")
    private Integer quantity;
    
    // 預設建構子
    public AddToCartRequest() {}
    
    // 建構子
    public AddToCartRequest(String customerId, Long productId, Integer quantity) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
    }
    
    // Getters and Setters
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public Long getProductId() {
        return productId;
    }
    
    public void setProductId(Long productId) {
        this.productId = productId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}