package com.microservices.order.dto;

import com.microservices.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 更新訂單狀態請求
 */
@Schema(description = "更新訂單狀態的請求物件")
public class UpdateOrderStatusRequest {
    
    @Schema(description = "新的訂單狀態", example = "CONFIRMED", required = true)
    @NotNull(message = "訂單狀態不能為空")
    private OrderStatus status;
    
    // 預設建構子
    public UpdateOrderStatusRequest() {}
    
    // 建構子
    public UpdateOrderStatusRequest(OrderStatus status) {
        this.status = status;
    }
    
    // Getters and Setters
    public OrderStatus getStatus() {
        return status;
    }
    
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}