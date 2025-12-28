package com.microservices.order.service.impl;

import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.OrderDTO;
import com.microservices.order.dto.OrderItemDTO;
import com.microservices.order.dto.PlaceOrderRequest;
import com.microservices.order.dto.UpdateOrderStatusRequest;
import com.microservices.order.entity.*;
import com.microservices.order.exception.CartNotFoundException;
import com.microservices.order.exception.OrderNotFoundException;
import com.microservices.order.mapper.OrderMapper;
import com.microservices.order.repository.CartRepository;
import com.microservices.order.repository.OrderRepository;
import com.microservices.order.service.OrderService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 訂單服務實現
 */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private CartRepository cartRepository;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private ProductServiceClient productServiceClient;
    
    @Autowired
    private InventoryServiceClient inventoryServiceClient;
    
    @Override
    public OrderDTO placeOrder(PlaceOrderRequest request) {
        logger.info("開始下單: customerId={}", request.getCustomerId());
        
        // 1. 獲取購物車
        Cart cart = cartRepository.findByCustomerIdWithItems(request.getCustomerId())
            .orElseThrow(() -> new CartNotFoundException("購物車為空或不存在"));
        
        if (cart.getItems().isEmpty()) {
            throw new CartNotFoundException("購物車為空");
        }
        
        // 2. 創建訂單
        String orderNumber = generateOrderNumber();
        BigDecimal totalAmount = calculateTotalAmount(cart);
        
        Order order = new Order(orderNumber, request.getCustomerId(), totalAmount);
        order = orderRepository.save(order);
        
        // 3. 轉換購物車項目為訂單項目並確認庫存預留
        for (CartItem cartItem : cart.getItems()) {
            // 確認庫存預留（從臨時預留轉為正式預留）
            confirmInventoryReservation(cartItem.getProductId(), request.getCustomerId(), 
                                      cartItem.getQuantity());
            
            // 創建訂單項目
            OrderItem orderItem = new OrderItem(
                cartItem.getProductId(),
                cartItem.getQuantity(),
                cartItem.getUnitPrice()
            );
            order.addItem(orderItem);
        }
        
        orderRepository.save(order);
        
        // 4. 清空購物車
        cartRepository.delete(cart);
        
        // 5. 返回訂單資訊
        Order savedOrder = orderRepository.findByIdWithItems(order.getId())
            .orElseThrow(() -> new OrderNotFoundException("訂單創建失敗"));
        
        OrderDTO orderDTO = orderMapper.toDTO(savedOrder);
        enrichOrderWithProductInfo(orderDTO);
        
        logger.info("成功創建訂單: orderNumber={}, customerId={}", orderNumber, request.getCustomerId());
        
        return orderDTO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrder(Long orderId) {
        logger.info("獲取訂單: orderId={}", orderId);
        
        Order order = orderRepository.findByIdWithItems(orderId)
            .orElseThrow(() -> new OrderNotFoundException("訂單不存在: " + orderId));
        
        OrderDTO orderDTO = orderMapper.toDTO(order);
        enrichOrderWithProductInfo(orderDTO);
        
        return orderDTO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderByNumber(String orderNumber) {
        logger.info("根據訂單號獲取訂單: orderNumber={}", orderNumber);
        
        Order order = orderRepository.findByOrderNumberWithItems(orderNumber)
            .orElseThrow(() -> new OrderNotFoundException("訂單不存在: " + orderNumber));
        
        OrderDTO orderDTO = orderMapper.toDTO(order);
        enrichOrderWithProductInfo(orderDTO);
        
        return orderDTO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<OrderDTO> getCustomerOrders(String customerId, Pageable pageable) {
        logger.info("獲取客戶訂單列表: customerId={}", customerId);
        
        Page<Order> orders = orderRepository.findByCustomerId(customerId, pageable);
        
        return orders.map(order -> {
            OrderDTO orderDTO = orderMapper.toDTO(order);
            enrichOrderWithProductInfo(orderDTO);
            return orderDTO;
        });
    }
    
    @Override
    public OrderDTO cancelOrder(Long orderId) {
        logger.info("取消訂單: orderId={}", orderId);
        
        Order order = orderRepository.findByIdWithItems(orderId)
            .orElseThrow(() -> new OrderNotFoundException("訂單不存在: " + orderId));
        
        if (!order.canBeCancelled()) {
            throw new IllegalStateException("訂單狀態不允許取消: " + order.getStatus());
        }
        
        // 釋放庫存預留
        for (OrderItem item : order.getItems()) {
            releaseInventoryReservation(item.getProductId(), order.getCustomerId(), 
                                      item.getQuantity());
        }
        
        // 更新訂單狀態
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        
        OrderDTO orderDTO = orderMapper.toDTO(order);
        enrichOrderWithProductInfo(orderDTO);
        
        logger.info("成功取消訂單: orderId={}", orderId);
        
        return orderDTO;
    }
    
    @Override
    public OrderDTO updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        logger.info("更新訂單狀態: orderId={}, newStatus={}", orderId, request.getStatus());
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("訂單不存在: " + orderId));
        
        order.setStatus(request.getStatus());
        orderRepository.save(order);
        
        OrderDTO orderDTO = orderMapper.toDTO(order);
        enrichOrderWithProductInfo(orderDTO);
        
        logger.info("成功更新訂單狀態: orderId={}, status={}", orderId, request.getStatus());
        
        return orderDTO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean orderExists(String orderNumber) {
        return orderRepository.existsByOrderNumber(orderNumber);
    }
    
    /**
     * 生成訂單號
     */
    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "ORD" + timestamp + random;
    }
    
    /**
     * 計算購物車總金額
     */
    private BigDecimal calculateTotalAmount(Cart cart) {
        return cart.getItems().stream()
            .map(CartItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 確認庫存預留
     */
    private void confirmInventoryReservation(Long productId, String customerId, Integer quantity) {
        try {
            InventoryServiceClient.ConfirmReservationRequest request = 
                new InventoryServiceClient.ConfirmReservationRequest(customerId, quantity);
            inventoryServiceClient.confirmReservation(productId, request);
        } catch (FeignException e) {
            logger.error("確認庫存預留失敗: productId={}, customerId={}, quantity={}", 
                        productId, customerId, quantity, e);
            throw new RuntimeException("庫存確認失敗");
        }
    }
    
    /**
     * 釋放庫存預留
     */
    private void releaseInventoryReservation(Long productId, String customerId, Integer quantity) {
        try {
            InventoryServiceClient.ReleaseInventoryRequest request = 
                new InventoryServiceClient.ReleaseInventoryRequest(customerId, quantity, "CONFIRMED");
            inventoryServiceClient.releaseInventory(productId, request);
        } catch (FeignException e) {
            logger.error("釋放庫存預留失敗: productId={}, customerId={}, quantity={}", 
                        productId, customerId, quantity, e);
            // 釋放庫存失敗不應該阻止訂單取消，只記錄錯誤
        }
    }
    
    /**
     * 豐富訂單資訊（添加產品名稱等）
     */
    private void enrichOrderWithProductInfo(OrderDTO orderDTO) {
        if (orderDTO.getItems() != null) {
            for (OrderItemDTO item : orderDTO.getItems()) {
                try {
                    ProductServiceClient.ProductDTO product = productServiceClient.getProduct(item.getProductId());
                    item.setProductName(product.getName());
                } catch (FeignException e) {
                    logger.warn("獲取產品資訊失敗: productId={}", item.getProductId());
                    item.setProductName("未知產品");
                }
            }
        }
    }
}