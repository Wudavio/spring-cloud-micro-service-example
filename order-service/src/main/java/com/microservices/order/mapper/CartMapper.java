package com.microservices.order.mapper;

import com.microservices.order.dto.CartDTO;
import com.microservices.order.dto.CartItemDTO;
import com.microservices.order.entity.Cart;
import com.microservices.order.entity.CartItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 購物車映射器
 */
@Component
public class CartMapper {
    
    /**
     * 實體轉 DTO
     */
    public CartDTO toDTO(Cart cart) {
        if (cart == null) {
            return null;
        }
        
        List<CartItemDTO> itemDTOs = cart.getItems().stream()
            .map(this::toItemDTO)
            .collect(Collectors.toList());
        
        BigDecimal totalAmount = itemDTOs.stream()
            .map(CartItemDTO::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new CartDTO(
            cart.getId(),
            cart.getCustomerId(),
            itemDTOs,
            totalAmount,
            cart.getUpdatedAt()
        );
    }
    
    /**
     * 購物車項目實體轉 DTO
     */
    public CartItemDTO toItemDTO(CartItem item) {
        if (item == null) {
            return null;
        }
        
        return new CartItemDTO(
            item.getId(),
            item.getProductId(),
            null, // 產品名稱需要從產品服務獲取
            item.getQuantity(),
            item.getUnitPrice(),
            item.getTotalPrice(),
            item.getCreatedAt()
        );
    }
    
    /**
     * DTO 轉實體
     */
    public Cart toEntity(CartDTO dto) {
        if (dto == null) {
            return null;
        }
        
        Cart cart = new Cart(dto.getCustomerId());
        cart.setId(dto.getId());
        cart.setUpdatedAt(dto.getUpdatedAt());
        
        return cart;
    }
    
    /**
     * 購物車項目 DTO 轉實體
     */
    public CartItem toItemEntity(CartItemDTO dto) {
        if (dto == null) {
            return null;
        }
        
        CartItem item = new CartItem(
            dto.getProductId(),
            dto.getQuantity(),
            dto.getUnitPrice()
        );
        item.setId(dto.getId());
        item.setCreatedAt(dto.getCreatedAt());
        
        return item;
    }
}