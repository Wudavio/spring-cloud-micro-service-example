package com.microservices.order.mapper;

import com.microservices.order.dto.OrderDTO;
import com.microservices.order.dto.OrderItemDTO;
import com.microservices.order.entity.Order;
import com.microservices.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 訂單映射器
 */
@Component
public class OrderMapper {
    
    /**
     * 實體轉 DTO
     */
    public OrderDTO toDTO(Order order) {
        if (order == null) {
            return null;
        }
        
        List<OrderItemDTO> itemDTOs = order.getItems().stream()
            .map(this::toItemDTO)
            .collect(Collectors.toList());
        
        return new OrderDTO(
            order.getId(),
            order.getOrderNumber(),
            order.getCustomerId(),
            order.getStatus(),
            order.getTotalAmount(),
            itemDTOs,
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
    
    /**
     * 訂單項目實體轉 DTO
     */
    public OrderItemDTO toItemDTO(OrderItem item) {
        if (item == null) {
            return null;
        }
        
        return new OrderItemDTO(
            item.getId(),
            item.getProductId(),
            null, // 產品名稱需要從產品服務獲取
            item.getQuantity(),
            item.getUnitPrice(),
            item.getTotalPrice()
        );
    }
    
    /**
     * DTO 轉實體
     */
    public Order toEntity(OrderDTO dto) {
        if (dto == null) {
            return null;
        }
        
        Order order = new Order(
            dto.getOrderNumber(),
            dto.getCustomerId(),
            dto.getTotalAmount()
        );
        order.setId(dto.getId());
        order.setStatus(dto.getStatus());
        order.setCreatedAt(dto.getCreatedAt());
        order.setUpdatedAt(dto.getUpdatedAt());
        
        return order;
    }
    
    /**
     * 訂單項目 DTO 轉實體
     */
    public OrderItem toItemEntity(OrderItemDTO dto) {
        if (dto == null) {
            return null;
        }
        
        OrderItem item = new OrderItem(
            dto.getProductId(),
            dto.getQuantity(),
            dto.getUnitPrice()
        );
        item.setId(dto.getId());
        
        return item;
    }
}