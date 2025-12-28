package com.microservices.order.service;

import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.exception.InsufficientInventoryException;
import com.microservices.order.repository.CartRepository;
import com.microservices.order.repository.CartItemRepository;
import feign.FeignException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
@SpringBootTest
@ActiveProfiles("test")
@Transactional
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
    @Property(tries = 100)
    @Label("庫存不足拒絕 - 庫存不足的產品添加到購物車應該被拒絕")
    void insufficientInventoryProductsShouldBeRejected(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer requestedQuantity) {
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬庫存不足（返回 400 錯誤）
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
            .thenThrow(new FeignException.BadRequest("Insufficient inventory", null, null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 驗證拋出 InsufficientInventoryException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(InsufficientInventoryException.class)
            .hasMessageContaining("庫存不足: productId=" + productId);
        
        // 驗證購物車沒有被創建或修改
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
    }
    
    /**
     * 屬性 3: 庫存不足拒絕 - 庫存充足的產品應該能成功添加
     */
    @Property(tries = 100)
    @Label("庫存不足拒絕 - 庫存充足的產品應該能成功添加（對比測試）")
    void sufficientInventoryProductsShouldBeAccepted(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer requestedQuantity) {
        
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
    }
    
    /**
     * 屬性 3: 庫存不足拒絕 - 部分庫存不足時應該拒絕整個請求
     */
    @Property(tries = 100)
    @Label("庫存不足拒絕 - 部分庫存不足時應該拒絕整個請求")
    void partialInsufficientInventoryShouldRejectEntireRequest(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 5, max = 20) Integer requestedQuantity) {
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬部分庫存不足（庫存服務返回錯誤）
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
            .thenThrow(new FeignException.BadRequest("Requested quantity exceeds available inventory", null, null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 驗證拋出 InsufficientInventoryException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(InsufficientInventoryException.class)
            .hasMessageContaining("庫存不足");
        
        // 驗證購物車沒有被創建
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
    }
    
    /**
     * 屬性 3: 庫存不足拒絕 - 庫存服務不可用時應該拋出運行時異常
     */
    @Property(tries = 50)
    @Label("庫存不足拒絕 - 庫存服務不可用時應該拋出運行時異常")
    void inventoryServiceUnavailableShouldThrowRuntimeException(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer requestedQuantity) {
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬庫存服務不可用（返回 500 錯誤）
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
            .thenThrow(new FeignException.InternalServerError("Service unavailable", null, null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 驗證拋出 RuntimeException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("庫存服務不可用");
        
        // 驗證購物車沒有被創建
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
    }
    
    /**
     * 屬性 3: 庫存不足拒絕 - 網路超時時應該拋出運行時異常
     */
    @Property(tries = 50)
    @Label("庫存不足拒絕 - 網路超時時應該拋出運行時異常")
    void networkTimeoutShouldThrowRuntimeException(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer requestedQuantity) {
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬網路超時
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
            .thenThrow(new FeignException.GatewayTimeout("Request timeout", null, null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, requestedQuantity);
        
        // 驗證拋出 RuntimeException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("庫存服務不可用");
        
        // 驗證購物車沒有被創建
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
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