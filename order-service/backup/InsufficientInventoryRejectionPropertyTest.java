package com.microservices.order.service;

import com.microservices.order.TestOrderServiceApplication;
import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.exception.InsufficientInventoryException;
import com.microservices.order.repository.CartRepository;
import com.microservices.order.repository.CartItemRepository;
import feign.FeignException;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;


import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 庫存不足拒絕屬性測試
 * Feature: microservices-order-inventory, Property 3: 庫存不足拒絕
 * 驗證需求: 需求 1.3
 */
@SpringBootTest(classes = TestOrderServiceApplication.class)
@ActiveProfiles("test")
class InsufficientInventoryRejectionPropertyTest {
    
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
     * 屬性 3: 庫存不足拒絕 - 庫存不足的產品添加到購物車應該被拒絕
     */
    @Test
    void insufficientInventoryProductsShouldBeRejected() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Long> productIdArb = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Integer> quantityArb = Arbitraries.integers().between(1, 10);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Long productId = productIdArb.sample();
            Integer requestedQuantity = quantityArb.sample();
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬庫存不足（返回 400 錯誤）
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
            .thenThrow(new feign.FeignException.BadRequest("Insufficient inventory", 
                feign.Request.create(feign.Request.HttpMethod.POST, "/inventory/reserve", 
                    java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8), null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 驗證拋出 InsufficientInventoryException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(InsufficientInventoryException.class)
            .hasMessageContaining("庫存不足: productId=" + productId);
        
        // 驗證購物車沒有被創建或修改
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 3: 庫存不足拒絕 - 庫存充足的產品應該能成功添加
     */
    @Test
    void sufficientInventoryProductsShouldBeAccepted() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Long> productIdArb = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Integer> quantityArb = Arbitraries.integers().between(1, 10);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Long productId = productIdArb.sample();
            Integer requestedQuantity = quantityArb.sample();
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬庫存充足（預留成功）
        InventoryServiceClient.ReservationDTO mockReservation = createMockReservation(productId, customerId, requestedQuantity);
        when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(mockReservation);
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 執行添加操作，應該成功
        assertThat(cartService.addToCart(request))
            .isNotNull()
            .satisfies(cart -> {
                assertThat(cart.getCustomerId()).isEqualTo(customerId);
                assertThat(cart.getItems()).hasSize(1);
                assertThat(cart.getItems().get(0).getProductId()).isEqualTo(productId);
                assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(requestedQuantity);
            });
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 3: 庫存不足拒絕 - 部分庫存不足時應該拒絕整個請求
     */
    @Test
    void partialInsufficientInventoryShouldRejectEntireRequest() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Long> productIdArb = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Integer> quantityArb = Arbitraries.integers().between(5, 20);
        
        // 運行多次迭代測試
        for (int i = 0; i < 100; i++) {
            String customerId = customerIdArb.sample();
            Long productId = productIdArb.sample();
            Integer requestedQuantity = quantityArb.sample();
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬部分庫存不足（庫存服務返回錯誤）
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
            .thenThrow(new feign.FeignException.BadRequest("Requested quantity exceeds available inventory", 
                feign.Request.create(feign.Request.HttpMethod.POST, "/inventory/reserve", 
                    java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8), null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 驗證拋出 InsufficientInventoryException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(InsufficientInventoryException.class)
            .hasMessageContaining("庫存不足");
        
        // 驗證購物車沒有被創建
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 3: 庫存不足拒絕 - 庫存服務不可用時應該拋出運行時異常
     */
    @Test
    void inventoryServiceUnavailableShouldThrowRuntimeException() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Long> productIdArb = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Integer> quantityArb = Arbitraries.integers().between(1, 10);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Long productId = productIdArb.sample();
            Integer requestedQuantity = quantityArb.sample();
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬庫存服務不可用（返回 500 錯誤）
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
            .thenThrow(new feign.FeignException.InternalServerError("Service unavailable", 
                feign.Request.create(feign.Request.HttpMethod.POST, "/inventory/reserve", 
                    java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8), null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 驗證拋出 RuntimeException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("庫存服務不可用");
        
        // 驗證購物車沒有被創建
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 3: 庫存不足拒絕 - 網路超時時應該拋出運行時異常
     */
    @Test
    void networkTimeoutShouldThrowRuntimeException() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<String> customerIdArb = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20);
        Arbitrary<Long> productIdArb = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Integer> quantityArb = Arbitraries.integers().between(1, 10);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            String customerId = customerIdArb.sample();
            Long productId = productIdArb.sample();
            Integer requestedQuantity = quantityArb.sample();
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬網路超時
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
            .thenThrow(new feign.FeignException.GatewayTimeout("Request timeout", 
                feign.Request.create(feign.Request.HttpMethod.POST, "/inventory/reserve", 
                    java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8), null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 驗證拋出 RuntimeException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("庫存服務不可用");
        
        // 驗證購物車沒有被創建
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
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
        product.setPrice(BigDecimal.valueOf(100.00));
        product.setCategory("測試分類");
        product.setStatus(status);
        return product;
    }
    
    /**
     * 創建模擬庫存預留
     */
    private InventoryServiceClient.ReservationDTO createMockReservation(Long productId, String customerId, Integer quantity) {
        InventoryServiceClient.ReservationDTO reservation = new InventoryServiceClient.ReservationDTO();
        reservation.setId(1L);
        reservation.setProductId(productId);
        reservation.setCustomerId(customerId);
        reservation.setQuantity(quantity);
        reservation.setType("TEMPORARY");
        reservation.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(30));
        return reservation;
    }
}