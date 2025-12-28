package com.microservices.order.repository;

import com.microservices.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 訂單項目資料存取介面
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    /**
     * 根據訂單ID查找所有項目
     */
    List<OrderItem> findByOrderId(Long orderId);
    
    /**
     * 根據產品ID查找所有訂單項目
     */
    List<OrderItem> findByProductId(Long productId);
    
    /**
     * 根據訂單ID刪除所有項目
     */
    void deleteByOrderId(Long orderId);
}