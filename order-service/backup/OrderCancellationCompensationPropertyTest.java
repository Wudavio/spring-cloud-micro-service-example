package com.microservices.order.service;

import com.microservices.order.TestOrderServiceApplication;
import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.OrderDTO;
import com.microservices.order.entity.Order;
import com.microservices.order.entity.OrderItem;
import com.microservices.order.entity.OrderStatus;
import com.microservices.order.repository.OrderRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 訂單取消補償屬性測試
 * Feature: microservices-order-inventory, Property 7: 訂單取消補償
 * 驗證需求: 需求 1.10
 */
@SpringBootTest(classes = TestOrderServiceApplication.class)
@ActiveProfiles("test")
class OrderCancellationCompensationPropertyTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @MockBean
    private ProductServiceClient productServiceClient;
    
    @MockBean
    private InventoryServiceClient inventoryServiceClient;
    
    @BeforeEach
    void setUp() {
        // 清理測試資料
        orderRepository.deleteAll();
        
        // 重置 Mock
        reset(productServiceClient, inventoryServiceClient);
    }
    
    /**
     * 屬性 7: 訂單取消補償 - 取消訂單應該釋放所有相關的庫存預留
     */
    @Test
    void cancellingOrderShouldReleaseAllInventoryReservations() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 5);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建可取消狀態的訂單
        Order order = setupOrderWithMultipleItems(customerId, itemCount, OrderStatus.PENDING);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(order.getItems());
        
        // 模擬庫存釋放成功
        doNothing().when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 執行取消操作
        OrderDTO result = orderService.cancelOrder(order.getId());
        
        // 驗證訂單狀態已更新為取消
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        
        // 驗證每個訂單項目的庫存都被釋放
        verify(inventoryServiceClient, times(itemCount)).releaseInventory(anyLong(), any());
        
        // 驗證釋放請求的詳細資訊
        ArgumentCaptor<Long> productIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<InventoryServiceClient.ReleaseInventoryRequest> requestCaptor = 
            ArgumentCaptor.forClass(InventoryServiceClient.ReleaseInventoryRequest.class);
        
        verify(inventoryServiceClient, times(itemCount))
            .releaseInventory(productIdCaptor.capture(), requestCaptor.capture());
        
        List<Long> capturedProductIds = productIdCaptor.getAllValues();
        List<InventoryServiceClient.ReleaseInventoryRequest> capturedRequests = requestCaptor.getAllValues();
        
        // 驗證每個項目的釋放請求
        for (int j = 0; j < itemCount; j++) {
            OrderItem orderItem = order.getItems().get(j);
            Long capturedProductId = capturedProductIds.get(j);
            InventoryServiceClient.ReleaseInventoryRequest capturedRequest = capturedRequests.get(j);
            
            assertThat(capturedProductId).isEqualTo(orderItem.getProductId());
            assertThat(capturedRequest.getCustomerId()).isEqualTo(customerId);
            assertThat(capturedRequest.getQuantity()).isEqualTo(orderItem.getQuantity());
            assertThat(capturedRequest.getType()).isEqualTo("CONFIRMED");
        }
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 7: 訂單取消補償 - 不可取消狀態的訂單應該拋出異常
     */
    @Test
    void nonCancellableOrdersShouldThrowException() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建不可取消狀態的訂單（已發貨）
        Order order = setupOrderWithMultipleItems(customerId, itemCount, OrderStatus.SHIPPED);
        
        // 執行取消操作
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("訂單狀態不允許取消");
        
        // 驗證沒有調用庫存釋放
        verify(inventoryServiceClient, never()).releaseInventory(anyLong(), any());
        
        // 驗證訂單狀態沒有改變
        Order unchangedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 7: 訂單取消補償 - 已取消的訂單再次取消應該拋出異常
     */
    @Test
    void alreadyCancelledOrdersShouldThrowException() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建已取消的訂單
        Order order = setupOrderWithMultipleItems(customerId, itemCount, OrderStatus.CANCELLED);
        
        // 執行取消操作
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("訂單狀態不允許取消");
        
        // 驗證沒有調用庫存釋放
        verify(inventoryServiceClient, never()).releaseInventory(anyLong(), any());
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 7: 訂單取消補償 - 庫存釋放失敗不應該阻止訂單取消
     */
    @Test
    void inventoryReleaseFailureShouldNotPreventOrderCancellation() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建可取消狀態的訂單
        Order order = setupOrderWithMultipleItems(customerId, itemCount, OrderStatus.CONFIRMED);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(order.getItems());
        
        // 模擬庫存釋放失敗
        doThrow(new RuntimeException("Inventory service unavailable"))
            .when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 執行取消操作，應該成功（庫存釋放失敗只記錄錯誤）
        OrderDTO result = orderService.cancelOrder(order.getId());
        
        // 驗證訂單狀態已更新為取消
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        
        // 驗證嘗試了庫存釋放
        verify(inventoryServiceClient, times(itemCount)).releaseInventory(anyLong(), any());
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 7: 訂單取消補償 - 部分庫存釋放失敗應該繼續處理其他項目
     */
    @Test
    void partialInventoryReleaseFailureShouldContinueProcessingOtherItems() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
        
        // 創建包含3個項目的訂單
        Order order = setupOrderWithMultipleItems(customerId, 3, OrderStatus.PENDING);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(order.getItems());
        
        // 模擬第二個項目的庫存釋放失敗，其他成功
        doNothing().when(inventoryServiceClient).releaseInventory(eq(1L), any());
        doThrow(new RuntimeException("Release failed for product 2"))
            .when(inventoryServiceClient).releaseInventory(eq(2L), any());
        doNothing().when(inventoryServiceClient).releaseInventory(eq(3L), any());
        
        // 執行取消操作
        OrderDTO result = orderService.cancelOrder(order.getId());
        
        // 驗證訂單狀態已更新為取消
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        
        // 驗證所有項目都嘗試了庫存釋放
        verify(inventoryServiceClient).releaseInventory(eq(1L), any());
        verify(inventoryServiceClient).releaseInventory(eq(2L), any());
        verify(inventoryServiceClient).releaseInventory(eq(3L), any());
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 7: 訂單取消補償 - 取消操作應該是冪等的
     */
    @Test
    void orderCancellationResultShouldBeConsistent() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建可取消狀態的訂單
        Order order = setupOrderWithMultipleItems(customerId, itemCount, OrderStatus.PENDING);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(order.getItems());
        
        // 模擬庫存釋放成功
        doNothing().when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 執行取消操作
        OrderDTO result = orderService.cancelOrder(order.getId());
        
        // 驗證取消結果的一致性
        assertThat(result.getId()).isEqualTo(order.getId());
        assertThat(result.getOrderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(result.getCustomerId()).isEqualTo(order.getCustomerId());
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(result.getItems()).hasSize(itemCount);
        
        // 驗證資料庫中的訂單狀態也已更新
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 7: 訂單取消補償 - 取消操作應該保持訂單的其他資訊不變
     */
    @Test
    void orderCancellationShouldPreserveOtherOrderInformation() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 5);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建可取消狀態的訂單
        Order originalOrder = setupOrderWithMultipleItems(customerId, itemCount, OrderStatus.CONFIRMED);
        
        // 記錄原始資訊
        String originalOrderNumber = originalOrder.getOrderNumber();
        String originalCustomerId = originalOrder.getCustomerId();
        BigDecimal originalTotalAmount = originalOrder.getTotalAmount();
        LocalDateTime originalCreatedAt = originalOrder.getCreatedAt();
        int originalItemCount = originalOrder.getItems().size();
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(originalOrder.getItems());
        
        // 模擬庫存釋放成功
        doNothing().when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 執行取消操作
        OrderDTO result = orderService.cancelOrder(originalOrder.getId());
        
        // 驗證除狀態外的其他資訊保持不變
        assertThat(result.getOrderNumber()).isEqualTo(originalOrderNumber);
        assertThat(result.getCustomerId()).isEqualTo(originalCustomerId);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(originalTotalAmount);
        assertThat(result.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(result.getItems()).hasSize(originalItemCount);
        
        // 驗證狀態已更新
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        
        // 驗證更新時間已改變
        assertThat(result.getUpdatedAt()).isAfter(originalCreatedAt);
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 設置包含多個項目的訂單
     */
    private Order setupOrderWithMultipleItems(String customerId, Integer itemCount, OrderStatus status) {
        String orderNumber = generateOrderNumber();
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        Order order = new Order(orderNumber, customerId, totalAmount);
        order.setStatus(status);
        order = orderRepository.save(order);
        
        for (int i = 1; i <= itemCount; i++) {
            Long productId = (long) i;
            Integer quantity = i;
            BigDecimal unitPrice = BigDecimal.valueOf(100.00 + i);
            
            OrderItem item = new OrderItem(productId, quantity, unitPrice);
            order.addItem(item);
            totalAmount = totalAmount.add(item.getTotalPrice());
        }
        
        order.setTotalAmount(totalAmount);
        return orderRepository.save(order);
    }
    
    /**
     * 為所有訂單項目模擬產品服務調用
     */
    private void mockProductServiceForAllItems(List<OrderItem> items) {
        for (OrderItem item : items) {
            ProductServiceClient.ProductDTO mockProduct = createMockProduct(item.getProductId(), "ACTIVE");
            when(productServiceClient.getProduct(item.getProductId())).thenReturn(mockProduct);
        }
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
     * 創建模擬產品
     */
    private ProductServiceClient.ProductDTO createMockProduct(Long productId, String status) {
        ProductServiceClient.ProductDTO product = new ProductServiceClient.ProductDTO();
        product.setId(productId);
        product.setName("測試產品 " + productId);
        product.setDescription("測試產品描述");
        product.setPrice(BigDecimal.valueOf(100.00 + productId));
        product.setCategory("測試分類");
        product.setStatus(status);
        return product;
    }
}