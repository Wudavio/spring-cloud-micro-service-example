package com.microservices.order.service;

import com.microservices.order.TestOrderServiceApplication;
import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.CartDTO;
import com.microservices.order.dto.CartItemDTO;
import com.microservices.order.entity.Cart;
import com.microservices.order.entity.CartItem;
import com.microservices.order.repository.CartRepository;
import com.microservices.order.repository.CartItemRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;


import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 購物車查詢完整性屬性測試
 * Feature: microservices-order-inventory, Property 4: 購物車查詢完整性
 * 驗證需求: 需求 1.6
 */
@SpringBootTest(classes = TestOrderServiceApplication.class)
@ActiveProfiles("test")
class CartQueryIntegrityPropertyTest {
    
    @Autowired
    private CartService cartService;
    
    @Autowired
    private CartRepository cartRepository;
    
    @Autowired
    private CartItemRepository cartItemRepository;
    
    @MockBean
    private ProductServiceClient productServiceClient;
    
    @MockBean
    private InventoryServiceClient inventoryServiceClient;
    
    @BeforeEach
    void setUp() {
        // 清理測試資料
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        
        // 重置 Mock
        reset(productServiceClient, inventoryServiceClient);
    }
    
    /**
     * 屬性 4: 購物車查詢完整性 - 查詢結果應該包含所有必要欄位
     */
    @Test
    void cartQueryShouldContainAllRequiredFields() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 5);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建購物車和項目
        Cart cart = setupCartWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(cart.getItems());
        
        // 執行查詢
        CartDTO result = cartService.getCart(customerId);
        
        // 驗證購物車基本資訊
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getUpdatedAt()).isNotNull();
        
        // 驗證購物車項目資訊完整性
        assertThat(result.getItems()).hasSize(itemCount);
        
        for (CartItemDTO item : result.getItems()) {
            // 驗證項目基本欄位
            assertThat(item.getId()).isNotNull();
            assertThat(item.getProductId()).isNotNull();
            assertThat(item.getQuantity()).isNotNull().isPositive();
            assertThat(item.getUnitPrice()).isNotNull().isPositive();
            assertThat(item.getTotalPrice()).isNotNull().isPositive();
            assertThat(item.getCreatedAt()).isNotNull();
            
            // 驗證產品名稱已被填充
            assertThat(item.getProductName()).isNotNull().isNotEmpty();
            
            // 驗證總價計算正確
            BigDecimal expectedTotalPrice = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            assertThat(item.getTotalPrice()).isEqualByComparingTo(expectedTotalPrice);
        }
        
        // 驗證購物車總金額
        assertThat(result.getTotalAmount()).isNotNull().isPositive();
        
        BigDecimal expectedTotalAmount = result.getItems().stream()
            .map(CartItemDTO::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotalAmount);
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 4: 購物車查詢完整性 - 空購物車查詢應該返回有效的空結果
     */
    @Test
    void emptyCartQueryShouldReturnValidEmptyResult() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
        
        // 不創建任何購物車項目，直接查詢
        CartDTO result = cartService.getCart(customerId);
        
        // 驗證空購物車的結構完整性
        assertThat(result).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getItems()).isNotNull().isEmpty();
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 4: 購物車查詢完整性 - 產品服務不可用時應該提供降級資訊
     */
    @Test
    void cartQueryShouldProvideGracefulDegradationWhenProductServiceUnavailable() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建購物車和項目
        setupCartWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務不可用
        when(productServiceClient.getProduct(anyLong()))
            .thenThrow(new feign.FeignException.InternalServerError("Product service unavailable", 
                feign.Request.create(feign.Request.HttpMethod.GET, "/products/1", 
                    java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8), null, null));
        
        // 執行查詢
        CartDTO result = cartService.getCart(customerId);
        
        // 驗證基本結構仍然完整
        assertThat(result).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getItems()).hasSize(itemCount);
        
        // 驗證降級處理：產品名稱應該顯示為"未知產品"
        for (CartItemDTO item : result.getItems()) {
            assertThat(item.getId()).isNotNull();
            assertThat(item.getProductId()).isNotNull();
            assertThat(item.getQuantity()).isNotNull().isPositive();
            assertThat(item.getUnitPrice()).isNotNull().isPositive();
            assertThat(item.getTotalPrice()).isNotNull().isPositive();
            assertThat(item.getProductName()).isEqualTo("未知產品");
        }
        
        // 驗證總金額計算仍然正確
        assertThat(result.getTotalAmount()).isNotNull().isPositive();
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 4: 購物車查詢完整性 - 查詢結果應該與資料庫狀態一致
     */
    @Test
    void cartQueryShouldBeConsistentWithDatabaseState() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 5);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建購物車和項目
        Cart cart = setupCartWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(cart.getItems());
        
        // 執行查詢
        CartDTO result = cartService.getCart(customerId);
        
        // 從資料庫重新獲取購物車進行對比
        Cart dbCart = cartRepository.findByCustomerIdWithItems(customerId).orElseThrow();
        
        // 驗證查詢結果與資料庫狀態一致
        assertThat(result.getId()).isEqualTo(dbCart.getId());
        assertThat(result.getCustomerId()).isEqualTo(dbCart.getCustomerId());
        assertThat(result.getItems()).hasSize(dbCart.getItems().size());
        
        // 驗證每個項目的一致性
        for (int j = 0; j < result.getItems().size(); j++) {
            CartItemDTO resultItem = result.getItems().get(j);
            CartItem dbItem = dbCart.getItems().get(j);
            
            assertThat(resultItem.getId()).isEqualTo(dbItem.getId());
            assertThat(resultItem.getProductId()).isEqualTo(dbItem.getProductId());
            assertThat(resultItem.getQuantity()).isEqualTo(dbItem.getQuantity());
            assertThat(resultItem.getUnitPrice()).isEqualByComparingTo(dbItem.getUnitPrice());
            assertThat(resultItem.getTotalPrice()).isEqualByComparingTo(dbItem.getTotalPrice());
        }
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 4: 購物車查詢完整性 - 多次查詢應該返回一致的結果
     */
    @Test
    void multipleCartQueriesShouldReturnConsistentResults() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 創建購物車和項目
        setupCartWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務調用
        mockProductServiceForAllItems(cartRepository.findByCustomerIdWithItems(customerId).orElseThrow().getItems());
        
        // 執行多次查詢
        CartDTO result1 = cartService.getCart(customerId);
        CartDTO result2 = cartService.getCart(customerId);
        CartDTO result3 = cartService.getCart(customerId);
        
        // 驗證多次查詢結果一致
        assertThat(result1.getId()).isEqualTo(result2.getId()).isEqualTo(result3.getId());
        assertThat(result1.getCustomerId()).isEqualTo(result2.getCustomerId()).isEqualTo(result3.getCustomerId());
        assertThat(result1.getItems()).hasSize(result2.getItems().size()).hasSize(result3.getItems().size());
        assertThat(result1.getTotalAmount()).isEqualByComparingTo(result2.getTotalAmount()).isEqualByComparingTo(result3.getTotalAmount());
        
        // 驗證項目詳細資訊一致
        for (int j = 0; j < result1.getItems().size(); j++) {
            CartItemDTO item1 = result1.getItems().get(j);
            CartItemDTO item2 = result2.getItems().get(j);
            CartItemDTO item3 = result3.getItems().get(j);
            
            assertThat(item1.getId()).isEqualTo(item2.getId()).isEqualTo(item3.getId());
            assertThat(item1.getProductId()).isEqualTo(item2.getProductId()).isEqualTo(item3.getProductId());
            assertThat(item1.getQuantity()).isEqualTo(item2.getQuantity()).isEqualTo(item3.getQuantity());
            assertThat(item1.getUnitPrice()).isEqualByComparingTo(item2.getUnitPrice()).isEqualByComparingTo(item3.getUnitPrice());
        }
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 設置包含多個項目的購物車
     */
    private Cart setupCartWithMultipleItems(String customerId, Integer itemCount) {
        Cart cart = new Cart(customerId);
        cart = cartRepository.save(cart);
        
        for (int i = 1; i <= itemCount; i++) {
            Long productId = (long) i;
            Integer quantity = i;
            BigDecimal unitPrice = BigDecimal.valueOf(100.00 + i);
            
            CartItem item = new CartItem(productId, quantity, unitPrice);
            cart.addItem(item);
            cartItemRepository.save(item);
        }
        
        return cartRepository.save(cart);
    }
    
    /**
     * 為所有購物車項目模擬產品服務調用
     */
    private void mockProductServiceForAllItems(List<CartItem> items) {
        for (CartItem item : items) {
            ProductServiceClient.ProductDTO mockProduct = createMockProduct(item.getProductId(), "ACTIVE");
            when(productServiceClient.getProduct(item.getProductId())).thenReturn(mockProduct);
        }
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