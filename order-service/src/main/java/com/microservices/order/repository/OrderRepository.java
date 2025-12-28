package com.microservices.order.repository;

import com.microservices.order.entity.Order;
import com.microservices.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 訂單資料存取介面
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    /**
     * 根據訂單號查找訂單
     */
    Optional<Order> findByOrderNumber(String orderNumber);
    
    /**
     * 根據客戶ID查找訂單（分頁）
     */
    Page<Order> findByCustomerId(String customerId, Pageable pageable);
    
    /**
     * 根據客戶ID和狀態查找訂單
     */
    List<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status);
    
    /**
     * 根據狀態查找訂單
     */
    List<Order> findByStatus(OrderStatus status);
    
    /**
     * 查找訂單及其項目
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);
    
    /**
     * 查找訂單及其項目（根據訂單號）
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);
    
    /**
     * 根據時間範圍查找訂單
     */
    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * 檢查訂單號是否存在
     */
    boolean existsByOrderNumber(String orderNumber);
}