package com.microservices.order.service;

import com.microservices.order.dto.OrderDTO;
import com.microservices.order.dto.PlaceOrderRequest;
import com.microservices.order.dto.UpdateOrderStatusRequest;
import com.microservices.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 訂單服務介面
 */
public interface OrderService {
    
    /**
     * 下單（從購物車創建訂單）
     */
    OrderDTO placeOrder(PlaceOrderRequest request);
    
    /**
     * 根據ID獲取訂單
     */
    OrderDTO getOrder(Long orderId);
    
    /**
     * 根據訂單號獲取訂單
     */
    OrderDTO getOrderByNumber(String orderNumber);
    
    /**
     * 獲取客戶訂單列表
     */
    Page<OrderDTO> getCustomerOrders(String customerId, Pageable pageable);
    
    /**
     * 取消訂單
     */
    OrderDTO cancelOrder(Long orderId);
    
    /**
     * 更新訂單狀態
     */
    OrderDTO updateOrderStatus(Long orderId, UpdateOrderStatusRequest request);
    
    /**
     * 檢查訂單是否存在
     */
    boolean orderExists(String orderNumber);
}