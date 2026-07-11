package com.microservices.order.service;

import com.microservices.order.TestOrderServiceApplication;
import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.dto.CartDTO;
import com.microservices.order.dto.UpdateCartItemRequest;
import com.microservices.order.entity.Cart;
import com.microservices.order.entity.CartItem;
import com.microservices.order.repository.CartRepository;
import com.microservices.order.repository.CartItemRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;


import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 購物車庫存同步屬性測試
 * Feature: microservices-order-inventory, Property 2: 購物車庫存同步
 * 驗證需求: 需求 1.2, 1.4, 1.5
 */
@SpringBootTest(classes = TestOrderServiceApplication.class)
@ActiveProfiles("test")
class CartInventorySyncPropertyTest {
    
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
     * 屬性 2: 購物車庫存同步 - 添加商品時庫存預留應該與購物車數量一致
     */
    @Test
    void addingItemsShouldSyncInventoryReservation() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<Long> customerIdArb = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<Long> productIdArb = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Integer> quantityArb = Arbitraries.integers().between(1, 10);
        
        // 運行多次迭代測試
        for (int i = 0; i < 10; i++) {
            Long customerId = customerIdArb.sample();
            Long productId = productIdArb.sample();
            Integer quantity = quantityArb.sample();
        
            // 模擬產品存在且狀態為 ACTIVE
            ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
            when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
            
            // 模擬庫存預留成功
            InventoryServiceClient.ReservationDTO mockReservation = createMockReservation(productId, customerId.toString(), quantity);
            when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(mockReservation);
            
            // 創建添加到購物車的請求
            AddToCartRequest request = new AddToCartRequest(productId, quantity);
            request.setUserId(customerId);
            
            // 執行添加操作
            CartDTO result = cartService.addToCart(request);
            
            // 驗證庫存預留調用
            ArgumentCaptor<InventoryServiceClient.ReserveInventoryRequest> captor = 
                ArgumentCaptor.forClass(InventoryServiceClient.ReserveInventoryRequest.class);
            verify(inventoryServiceClient).reserveInventory(eq(productId), captor.capture());
            
            InventoryServiceClient.ReserveInventoryRequest reserveRequest = captor.getValue();
            
            // 驗證庫存預留數量與購物車數量一致
            assertThat(reserveRequest.getQuantity()).isEqualTo(quantity);
            assertThat(reserveRequest.getUserId()).isEqualTo(customerId);
            assertThat(reserveRequest.getType()).isEqualTo("TEMPORARY");
            
            // 驗證購物車中的數量
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getQuantity()).isEqualTo(quantity);
            
            // 清理數據和 Mock
            cartItemRepository.deleteAll();
            cartRepository.deleteAll();
            reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 2: 購物車庫存同步 - 更新商品數量時庫存預留應該相應調整
     */
    @Test
    void updatingItemQuantityShouldAdjustInventoryReservation() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<Long> customerIdArb = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<Long> productIdArb = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Integer> initialQuantityArb = Arbitraries.integers().between(1, 5);
        Arbitrary<Integer> newQuantityArb = Arbitraries.integers().between(1, 10);
        
        // 運行多次迭代測試
        for (int i = 0; i < 10; i++) {
            Long customerId = customerIdArb.sample();
            Long productId = productIdArb.sample();
            Integer initialQuantity = initialQuantityArb.sample();
            Integer newQuantity = newQuantityArb.sample();
        
        // 先添加商品到購物車
        setupCartWithItem(customerId, productId, initialQuantity);
        
        // 重置 Mock 以便捕獲更新操作的調用
        reset(inventoryServiceClient);
        
        // 模擬庫存調整成功
        when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(createMockReservation(productId, customerId.toString(), Math.abs(newQuantity - initialQuantity)));
        doNothing().when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 獲取購物車項目ID
        Cart cart = cartRepository.findByUserIdWithItems(customerId).orElseThrow();
        CartItem item = cart.getItems().get(0);
        
        // 創建更新請求
        UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(customerId, newQuantity);
        
        // 執行更新操作
        CartDTO result = cartService.updateCartItem(item.getId(), updateRequest);
        
        // 驗證庫存調整
        int difference = newQuantity - initialQuantity;
        if (difference > 0) {
            // 需要增加預留
            ArgumentCaptor<InventoryServiceClient.ReserveInventoryRequest> reserveCaptor = 
                ArgumentCaptor.forClass(InventoryServiceClient.ReserveInventoryRequest.class);
            verify(inventoryServiceClient).reserveInventory(eq(productId), reserveCaptor.capture());
            
            InventoryServiceClient.ReserveInventoryRequest reserveRequest = reserveCaptor.getValue();
            assertThat(reserveRequest.getQuantity()).isEqualTo(difference);
            assertThat(reserveRequest.getUserId()).isEqualTo(customerId);
            assertThat(reserveRequest.getType()).isEqualTo("TEMPORARY");
        } else if (difference < 0) {
            // 需要減少預留
            ArgumentCaptor<InventoryServiceClient.ReleaseInventoryRequest> releaseCaptor = 
                ArgumentCaptor.forClass(InventoryServiceClient.ReleaseInventoryRequest.class);
            verify(inventoryServiceClient).releaseInventory(eq(productId), releaseCaptor.capture());
            
            InventoryServiceClient.ReleaseInventoryRequest releaseRequest = releaseCaptor.getValue();
            assertThat(releaseRequest.getQuantity()).isEqualTo(Math.abs(difference));
            assertThat(releaseRequest.getUserId()).isEqualTo(customerId);
            assertThat(releaseRequest.getReleaseType()).isEqualTo("TEMPORARY");
        }
        // difference == 0 時不需要調整庫存
        
        // 驗證購物車中的數量已更新
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(newQuantity);
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 2: 購物車庫存同步 - 移除商品時應該釋放所有預留庫存
     */
    @Test
    void removingItemsShouldReleaseAllReservedInventory() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<Long> customerIdArb = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<Long> productIdArb = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<Integer> quantityArb = Arbitraries.integers().between(1, 10);
        
        // 運行多次迭代測試
        for (int i = 0; i < 10; i++) {
            Long customerId = customerIdArb.sample();
            Long productId = productIdArb.sample();
            Integer quantity = quantityArb.sample();
        
        // 先添加商品到購物車
        setupCartWithItem(customerId, productId, quantity);
        
        // 重置 Mock 以便捕獲移除操作的調用
        reset(inventoryServiceClient);
        doNothing().when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 獲取購物車項目ID
        Cart cart = cartRepository.findByUserIdWithItems(customerId).orElseThrow();
        CartItem item = cart.getItems().get(0);
        
        // 執行移除操作
        CartDTO result = cartService.removeFromCart(item.getId(), customerId);
        
        // 驗證庫存釋放
        ArgumentCaptor<InventoryServiceClient.ReleaseInventoryRequest> captor = 
            ArgumentCaptor.forClass(InventoryServiceClient.ReleaseInventoryRequest.class);
        verify(inventoryServiceClient).releaseInventory(eq(productId), captor.capture());
        
        InventoryServiceClient.ReleaseInventoryRequest releaseRequest = captor.getValue();
        assertThat(releaseRequest.getQuantity()).isEqualTo(quantity);
        assertThat(releaseRequest.getUserId()).isEqualTo(customerId);
        assertThat(releaseRequest.getReleaseType()).isEqualTo("TEMPORARY");
        
        // 驗證購物車為空
        assertThat(result.getItems()).isEmpty();
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 屬性 2: 購物車庫存同步 - 清空購物車時應該釋放所有商品的預留庫存
     */
    @Test
    void clearingCartShouldReleaseAllInventoryReservations() {
        // 使用 jqwik 生成器創建測試數據
        Arbitrary<Long> customerIdArb = Arbitraries.longs().between(1L, 10000L);
        Arbitrary<Integer> itemCountArb = Arbitraries.integers().between(1, 3);
        
        // 運行多次迭代測試
        for (int i = 0; i < 50; i++) {
            Long customerId = customerIdArb.sample();
            Integer itemCount = itemCountArb.sample();
        
        // 添加多個商品到購物車
        for (int j = 1; j <= itemCount; j++) {
            Long productId = (long) j;
            Integer quantity = j + 1;
            setupCartWithItem(customerId, productId, quantity);
        }
        
        // 重置 Mock 以便捕獲清空操作的調用
        reset(inventoryServiceClient);
        doNothing().when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 執行清空購物車操作
        cartService.clearCart(customerId);
        
        // 驗證每個商品的庫存都被釋放
        verify(inventoryServiceClient, times(itemCount)).releaseInventory(anyLong(), any());
        
        // 驗證購物車已被刪除
        assertThat(cartRepository.findByUserId(customerId)).isEmpty();
        
        // 清理數據和 Mock
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        reset(productServiceClient, inventoryServiceClient);
        }
    }
    
    /**
     * 設置購物車和商品項目
     */
    private void setupCartWithItem(Long customerId, Long productId, Integer quantity) {
        // 模擬產品存在
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬庫存預留成功
        InventoryServiceClient.ReservationDTO mockReservation = createMockReservation(productId, customerId.toString(), quantity);
        when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(mockReservation);
        
        // 獲取或創建購物車
        Cart cart = cartRepository.findByUserId(customerId)
            .orElseGet(() -> cartRepository.save(new Cart(customerId)));
        
        // 檢查是否已有該商品 - 使用資料庫查詢而不是集合訪問
        boolean itemExists = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId).isPresent();
        
        if (!itemExists) {
            // 直接創建購物車項目，不使用 Cart.addItem 方法
            CartItem item = new CartItem(productId, quantity, mockProduct.getPrice());
            item.setCart(cart); // 直接設置關聯
            cartItemRepository.save(item);
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
        reservation.setUserId(customerId);
        reservation.setQuantity(quantity);
        reservation.setType("TEMPORARY");
        reservation.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(30));
        return reservation;
    }
}