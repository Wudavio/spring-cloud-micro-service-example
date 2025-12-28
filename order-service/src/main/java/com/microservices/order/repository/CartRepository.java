package com.microservices.order.repository;

import com.microservices.order.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 購物車資料存取介面
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    
    /**
     * 根據客戶ID查找購物車
     */
    Optional<Cart> findByCustomerId(String customerId);
    
    /**
     * 根據客戶ID刪除購物車
     */
    void deleteByCustomerId(String customerId);
    
    /**
     * 檢查客戶是否有購物車
     */
    boolean existsByCustomerId(String customerId);
    
    /**
     * 查找購物車及其項目
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.customerId = :customerId")
    Optional<Cart> findByCustomerIdWithItems(@Param("customerId") String customerId);
}