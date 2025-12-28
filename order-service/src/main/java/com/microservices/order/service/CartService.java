package com.microservices.order.service;

import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.dto.CartDTO;
import com.microservices.order.dto.UpdateCartItemRequest;

/**
 * 購物車服務介面
 */
public interface CartService {
    
    /**
     * 添加商品到購物車
     */
    CartDTO addToCart(AddToCartRequest request);
    
    /**
     * 更新購物車項目數量
     */
    CartDTO updateCartItem(Long itemId, UpdateCartItemRequest request);
    
    /**
     * 從購物車移除商品
     */
    CartDTO removeFromCart(Long itemId, Long userId);
    
    /**
     * 獲取購物車
     */
    CartDTO getCart(Long userId);
    
    /**
     * 清空購物車
     */
    void clearCart(Long userId);
    
    /**
     * 檢查購物車是否存在
     */
    boolean cartExists(Long userId);
}