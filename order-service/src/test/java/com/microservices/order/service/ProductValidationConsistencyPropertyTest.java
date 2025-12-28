package com.microservices.order.service;

import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.dto.CartDTO;
import com.microservices.order.exception.ProductNotFoundException;
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
import static org.mockito.Mockito.when;

/**
 * 產品驗證一致性屬性測試
 * Feature: microservices-order-inventory, Property 1: 產品驗證一致性
 * 驗證需求: 需求 1.1
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductValidationConsistencyPropertyTest {
    
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
    }
    
    /**
     * 屬性 1: 產品驗證一致性
     * 對於任何產品ID，當添加到購物車時，只有在產品服務中存在的產品才能成功添加，不存在的產品應該被拒絕
     */
    @Property(tries = 100)
    @Label("產品驗證一致性 - 存在的產品應該能成功添加到購物車")
    void existingProductsShouldBeAddedToCartSuccessfully(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer quantity) {
        
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬庫存預留成功
        InventoryServiceClient.ReservationDTO mockReservation = createMockReservation(productId, customerId, quantity);
        when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(mockReservation);
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, quantity);
        
        // 執行添加操作
        CartDTO result = cartService.addToCart(request);
        
        // 驗證結果
        assertThat(result).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProductId()).isEqualTo(productId);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(quantity);
    }
    
    @Property(tries = 100)
    @Label("產品驗證一致性 - 不存在的產品應該被拒絕")
    void nonExistentProductsShouldBeRejected(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer quantity) {
        
        // 模擬產品不存在（返回 404 錯誤）
        when(productServiceClient.getProduct(productId))
            .thenThrow(new FeignException.NotFound("Product not found", null, null, null));
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, quantity);
        
        // 驗證拋出 ProductNotFoundException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining("產品不存在: " + productId);
    }
    
    @Property(tries = 100)
    @Label("產品驗證一致性 - 非活躍狀態的產品應該被拒絕")
    void inactiveProductsShouldBeRejected(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer quantity) {
        
        // 模擬產品存在但狀態為 INACTIVE
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "INACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, quantity);
        
        // 驗證拋出 ProductNotFoundException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining("產品不可用: " + productId);
    }
    
    @Property(tries = 100)
    @Label("產品驗證一致性 - 已停產的產品應該被拒絕")
    void discontinuedProductsShouldBeRejected(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer quantity) {
        
        // 模擬產品存在但狀態為 DISCONTINUED
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "DISCONTINUED");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(customerId, productId, quantity);
        
        // 驗證拋出 ProductNotFoundException
        assertThatThrownBy(() -> cartService.addToCart(request))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining("產品不可用: " + productId);
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