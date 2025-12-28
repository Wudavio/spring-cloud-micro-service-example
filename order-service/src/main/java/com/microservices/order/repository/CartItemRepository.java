package com.microservices.order.repository;

import com.microservices.order.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 購物車項目資料存取介面
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    
    /**
     * 根據購物車ID查找所有項目
     */
    List<CartItem> findByCartId(Long cartId);
    
    /**
     * 根據購物車ID和產品ID查找項目
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.productId = :productId")
    Optional<CartItem> findByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);
    
    /**
     * 根據購物車ID刪除所有項目
     */
    void deleteByCartId(Long cartId);
    
    /**
     * 根據產品ID查找所有購物車項目
     */
    List<CartItem> findByProductId(Long productId);
}