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
     * 根據用戶ID查找購物車
     */
    Optional<Cart> findByUserId(Long userId);
    
    /**
     * 根據用戶ID刪除購物車
     */
    void deleteByUserId(Long userId);
    
    /**
     * 檢查用戶是否有購物車
     */
    boolean existsByUserId(Long userId);
    
    /**
     * 查找購物車及其項目
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.userId = :userId")
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);
}