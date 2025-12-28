package com.microservices.order.service;

import com.microservices.order.TestOrderServiceApplication;
import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.OrderDTO;
import com.microservices.order.dto.OrderItemDTO;
import com.microservices.order.entity.Order;
import com.microservices.order.entity.OrderItem;
import com.microservices.order.entity.OrderStatus;
import com.microservices.order.repository.OrderRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 訂單查詢完整性屬性測試
 * Feature: microservices-order-inventory, Property 6: 訂單查詢完整性
 * 驗證需求: 需求 1.9
 */
@SpringBootTest(classes = TestOrderServiceApplication.class)
@ActiveProfiles("test")
class OrderQueryIntegrityPropertyTest {
    
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
     * 屬性 6: 訂單查詢完整性 - 根據ID查詢應該返回完整的訂單資訊
     */
    @Test
    void orderQueryByIdShouldReturnCompleteInformation() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 5);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建訂單
        Order order = setupOrderWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(order.getItems());
        
        // 執行查詢
        OrderDTO result = orderService.getOrder(order.getId());
        
        // 驗證訂單基本資訊完整性
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(order.getId());
        assertThat(result.getOrderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(result.getCustomerId()).isEqualTo(order.getCustomerId());
        assertThat(result.getStatus()).isEqualTo(order.getStatus());
        assertThat(result.getTotalAmount()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(result.getCreatedAt()).isEqualTo(order.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(order.getUpdatedAt());
        
        // 驗證訂單項目完整性
        assertThat(result.getItems()).hasSize(itemCount);
        
        for (int j = 0; j < result.getItems().size(); j++) {
            OrderItemDTO resultItem = result.getItems().get(j);
            OrderItem orderItem = order.getItems().get(j);
            
            // 驗證項目基本欄位
            assertThat(resultItem.getId()).isEqualTo(orderItem.getId());
            assertThat(resultItem.getProductId()).isEqualTo(orderItem.getProductId());
            assertThat(resultItem.getQuantity()).isEqualTo(orderItem.getQuantity());
            assertThat(resultItem.getUnitPrice()).isEqualByComparingTo(orderItem.getUnitPrice());
            assertThat(resultItem.getTotalPrice()).isEqualByComparingTo(orderItem.getTotalPrice());
            
            // 驗證產品名稱已被填充
            assertThat(resultItem.getProductName()).isNotNull().isNotEmpty();
        }
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 6: 訂單查詢完整性 - 根據訂單號查詢應該返回相同的完整資訊
     */
    @Test
    void orderQueryByNumberShouldReturnSameCompleteInformation() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建訂單
        Order order = setupOrderWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(order.getItems());
        
        // 分別根據ID和訂單號查詢
        OrderDTO resultById = orderService.getOrder(order.getId());
        OrderDTO resultByNumber = orderService.getOrderByNumber(order.getOrderNumber());
        
        // 驗證兩種查詢方式返回相同的完整資訊
        assertThat(resultById.getId()).isEqualTo(resultByNumber.getId());
        assertThat(resultById.getOrderNumber()).isEqualTo(resultByNumber.getOrderNumber());
        assertThat(resultById.getCustomerId()).isEqualTo(resultByNumber.getCustomerId());
        assertThat(resultById.getStatus()).isEqualTo(resultByNumber.getStatus());
        assertThat(resultById.getTotalAmount()).isEqualByComparingTo(resultByNumber.getTotalAmount());
        assertThat(resultById.getItems()).hasSize(resultByNumber.getItems().size());
        
        // 驗證項目詳細資訊一致
        for (int j = 0; j < resultById.getItems().size(); j++) {
            OrderItemDTO itemById = resultById.getItems().get(j);
            OrderItemDTO itemByNumber = resultByNumber.getItems().get(j);
            
            assertThat(itemById.getId()).isEqualTo(itemByNumber.getId());
            assertThat(itemById.getProductId()).isEqualTo(itemByNumber.getProductId());
            assertThat(itemById.getQuantity()).isEqualTo(itemByNumber.getQuantity());
            assertThat(itemById.getUnitPrice()).isEqualByComparingTo(itemByNumber.getUnitPrice());
            assertThat(itemById.getTotalPrice()).isEqualByComparingTo(itemByNumber.getTotalPrice());
            assertThat(itemById.getProductName()).isEqualTo(itemByNumber.getProductName());
        }
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 6: 訂單查詢完整性 - 客戶訂單列表查詢應該返回完整的分頁資訊
     */
    @Test
    void customerOrdersQueryShouldReturnCompletePageInformation() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> orderCountArb = Arbitraries.integers().between(2, 5);
        Arbitrary<Integer> pageSizeArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer orderCount = orderCountArb.sample();
            Integer pageSize = pageSizeArb.sample();
        
        // 創建多個訂單
        for (int j = 1; j <= orderCount; j++) {
            setupOrderWithMultipleItems(customerId, 2);
        }
        
        // 模擬產品服務調用
        when(productServiceClient.getProduct(anyLong())).thenReturn(createMockProduct(1L, "ACTIVE"));
        
        // 執行分頁查詢
        Pageable pageable = PageRequest.of(0, pageSize);
        Page<OrderDTO> result = orderService.getCustomerOrders(customerId, pageable);
        
        // 驗證分頁資訊完整性
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo((long) orderCount);
        assertThat(result.getTotalPages()).isEqualTo((int) Math.ceil((double) orderCount / pageSize));
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(pageSize);
        assertThat(result.getNumberOfElements()).isEqualTo(Math.min(pageSize, orderCount));
        
        // 驗證每個訂單的完整性
        for (OrderDTO order : result.getContent()) {
            assertThat(order.getId()).isNotNull();
            assertThat(order.getOrderNumber()).isNotNull();
            assertThat(order.getCustomerId()).isEqualTo(customerId);
            assertThat(order.getStatus()).isNotNull();
            assertThat(order.getTotalAmount()).isNotNull().isPositive();
            assertThat(order.getCreatedAt()).isNotNull();
            assertThat(order.getUpdatedAt()).isNotNull();
            
            // 驗證訂單項目（如果有的話）
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                for (OrderItemDTO item : order.getItems()) {
                    assertThat(item.getId()).isNotNull();
                    assertThat(item.getProductId()).isNotNull();
                    assertThat(item.getQuantity()).isNotNull().isPositive();
                    assertThat(item.getUnitPrice()).isNotNull().isPositive();
                    assertThat(item.getTotalPrice()).isNotNull().isPositive();
                }
            }
        }
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 6: 訂單查詢完整性 - 產品服務不可用時應該提供降級資訊
     */
    @Test
    void orderQueryShouldProvideGracefulDegradationWhenProductServiceUnavailable() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建訂單
        Order order = setupOrderWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務不可用
        when(productServiceClient.getProduct(anyLong()))
            .thenThrow(new feign.FeignException.InternalServerError("Product service unavailable", 
                feign.Request.create(feign.Request.HttpMethod.GET, "/products/1", 
                    java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8), null, null));
        
        // 執行查詢
        OrderDTO result = orderService.getOrder(order.getId());
        
        // 驗證基本結構仍然完整
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(order.getId());
        assertThat(result.getOrderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(result.getCustomerId()).isEqualTo(order.getCustomerId());
        assertThat(result.getStatus()).isEqualTo(order.getStatus());
        assertThat(result.getTotalAmount()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(result.getItems()).hasSize(itemCount);
        
        // 驗證降級處理：產品名稱應該顯示為"未知產品"
        for (OrderItemDTO item : result.getItems()) {
            assertThat(item.getId()).isNotNull();
            assertThat(item.getProductId()).isNotNull();
            assertThat(item.getQuantity()).isNotNull().isPositive();
            assertThat(item.getUnitPrice()).isNotNull().isPositive();
            assertThat(item.getTotalPrice()).isNotNull().isPositive();
            assertThat(item.getProductName()).isEqualTo("未知產品");
        }
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 6: 訂單查詢完整性 - 多次查詢應該返回一致的結果
     */
    @Test
    void multipleOrderQueriesShouldReturnConsistentResults() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建訂單
        Order order = setupOrderWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(order.getItems());
        
        // 執行多次查詢
        OrderDTO result1 = orderService.getOrder(order.getId());
        OrderDTO result2 = orderService.getOrder(order.getId());
        OrderDTO result3 = orderService.getOrder(order.getId());
        
        // 驗證多次查詢結果一致
        assertThat(result1.getId()).isEqualTo(result2.getId()).isEqualTo(result3.getId());
        assertThat(result1.getOrderNumber()).isEqualTo(result2.getOrderNumber()).isEqualTo(result3.getOrderNumber());
        assertThat(result1.getCustomerId()).isEqualTo(result2.getCustomerId()).isEqualTo(result3.getCustomerId());
        assertThat(result1.getStatus()).isEqualTo(result2.getStatus()).isEqualTo(result3.getStatus());
        assertThat(result1.getTotalAmount()).isEqualByComparingTo(result2.getTotalAmount()).isEqualByComparingTo(result3.getTotalAmount());
        assertThat(result1.getItems()).hasSize(result2.getItems().size()).hasSize(result3.getItems().size());
        
        // 驗證項目詳細資訊一致
        for (int j = 0; j < result1.getItems().size(); j++) {
            OrderItemDTO item1 = result1.getItems().get(j);
            OrderItemDTO item2 = result2.getItems().get(j);
            OrderItemDTO item3 = result3.getItems().get(j);
            
            assertThat(item1.getId()).isEqualTo(item2.getId()).isEqualTo(item3.getId());
            assertThat(item1.getProductId()).isEqualTo(item2.getProductId()).isEqualTo(item3.getProductId());
            assertThat(item1.getQuantity()).isEqualTo(item2.getQuantity()).isEqualTo(item3.getQuantity());
            assertThat(item1.getUnitPrice()).isEqualByComparingTo(item2.getUnitPrice()).isEqualByComparingTo(item3.getUnitPrice());
            assertThat(item1.getProductName()).isEqualTo(item2.getProductName()).isEqualTo(item3.getProductName());
        }
        
        // 清理數據和 Mock
        orderRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 設置包含多個項目的訂單
     */
    private Order setupOrderWithMultipleItems(String customerId, Integer itemCount) {
        String orderNumber = generateOrderNumber();
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        Order order = new Order(orderNumber, customerId, totalAmount);
        order.setStatus(OrderStatus.PENDING);
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