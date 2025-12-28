package com.microservices.order.service;

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
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
@SpringBootTest
@ActiveProfiles("test")
@Transactional
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
    @Property(tries = 100)
    @Label("購物車庫存同步 - 添加商品時庫存預留數量應該與購物車數量一致")
    void addingItemsShouldSyncInventoryReservation(
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
        
        // 驗證庫存預留調用
        ArgumentCaptor<InventoryServiceClient.ReserveInventoryRequest> captor = 
            ArgumentCaptor.forClass(InventoryServiceClient.ReserveInventoryRequest.class);
        verify(inventoryServiceClient).reserveInventory(eq(productId), captor.capture());
        
        InventoryServiceClient.ReserveInventoryRequest reserveRequest = captor.getValue();
        
        // 驗證庫存預留數量與購物車數量一致
        assertThat(reserveRequest.getQuantity()).isEqualTo(quantity);
        assertThat(reserveRequest.getCustomerId()).isEqualTo(customerId);
        assertThat(reserveRequest.getType()).isEqualTo("TEMPORARY");
        
        // 驗證購物車中的數量
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(quantity);
    }
    
    /**
     * 屬性 2: 購物車庫存同步 - 更新商品數量時庫存預留應該相應調整
     */
    @Property(tries = 100)
    @Label("購物車庫存同步 - 更新商品數量時庫存預留應該相應調整")
    void updatingItemQuantityShouldAdjustInventoryReservation(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 5) Integer initialQuantity,
            @ForAll @IntRange(min = 1, max = 10) Integer newQuantity) {
        
        // 先添加商品到購物車
        setupCartWithItem(customerId, productId, initialQuantity);
        
        // 重置 Mock 以便捕獲更新操作的調用
        reset(inventoryServiceClient);
        
        // 模擬庫存調整成功
        when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(createMockReservation(productId, customerId, Math.abs(newQuantity - initialQuantity)));
        doNothing().when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 獲取購物車項目ID
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId).orElseThrow();
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
            assertThat(reserveRequest.getCustomerId()).isEqualTo(customerId);
            assertThat(reserveRequest.getType()).isEqualTo("TEMPORARY");
        } else if (difference < 0) {
            // 需要減少預留
            ArgumentCaptor<InventoryServiceClient.ReleaseInventoryRequest> releaseCaptor = 
                ArgumentCaptor.forClass(InventoryServiceClient.ReleaseInventoryRequest.class);
            verify(inventoryServiceClient).releaseInventory(eq(productId), releaseCaptor.capture());
            
            InventoryServiceClient.ReleaseInventoryRequest releaseRequest = releaseCaptor.getValue();
            assertThat(releaseRequest.getQuantity()).isEqualTo(Math.abs(difference));
            assertThat(releaseRequest.getCustomerId()).isEqualTo(customerId);
            assertThat(releaseRequest.getType()).isEqualTo("TEMPORARY");
        }
        // difference == 0 時不需要調整庫存
        
        // 驗證購物車中的數量已更新
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(newQuantity);
    }
    
    /**
     * 屬性 2: 購物車庫存同步 - 移除商品時應該釋放所有預留庫存
     */
    @Property(tries = 100)
    @Label("購物車庫存同步 - 移除商品時應該釋放所有預留庫存")
    void removingItemsShouldReleaseAllReservedInventory(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 1000) Long productId,
            @ForAll @IntRange(min = 1, max = 10) Integer quantity) {
        
        // 先添加商品到購物車
        setupCartWithItem(customerId, productId, quantity);
        
        // 重置 Mock 以便捕獲移除操作的調用
        reset(inventoryServiceClient);
        doNothing().when(inventoryServiceClient).releaseInventory(anyLong(), any());
        
        // 獲取購物車項目ID
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId).orElseThrow();
        CartItem item = cart.getItems().get(0);
        
        // 執行移除操作
        CartDTO result = cartService.removeFromCart(item.getId(), customerId);
        
        // 驗證庫存釋放
        ArgumentCaptor<InventoryServiceClient.ReleaseInventoryRequest> captor = 
            ArgumentCaptor.forClass(InventoryServiceClient.ReleaseInventoryRequest.class);
        verify(inventoryServiceClient).releaseInventory(eq(productId), captor.capture());
        
        InventoryServiceClient.ReleaseInventoryRequest releaseRequest = captor.getValue();
        assertThat(releaseRequest.getQuantity()).isEqualTo(quantity);
        assertThat(releaseRequest.getCustomerId()).isEqualTo(customerId);
        assertThat(releaseRequest.getType()).isEqualTo("TEMPORARY");
        
        // 驗證購物車為空
        assertThat(result.getItems()).isEmpty();
    }
    
    /**
     * 屬性 2: 購物車庫存同步 - 清空購物車時應該釋放所有商品的預留庫存
     */
    @Property(tries = 50)
    @Label("購物車庫存同步 - 清空購物車時應該釋放所有商品的預留庫存")
    void clearingCartShouldReleaseAllInventoryReservations(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 3) Integer itemCount) {
        
        // 添加多個商品到購物車
        for (int i = 1; i <= itemCount; i++) {
            Long productId = (long) i;
            Integer quantity = i + 1;
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
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
    }
    
    /**
     * 設置購物車和商品項目
     */
    private void setupCartWithItem(String customerId, Long productId, Integer quantity) {
        // 模擬產品存在
        ProductServiceClient.ProductDTO mockProduct = createMockProduct(productId, "ACTIVE");
        when(productServiceClient.getProduct(productId)).thenReturn(mockProduct);
        
        // 模擬庫存預留成功
        InventoryServiceClient.ReservationDTO mockReservation = createMockReservation(productId, customerId, quantity);
        when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(mockReservation);
        
        // 獲取或創建購物車
        Cart cart = cartRepository.findByCustomerId(customerId)
            .orElseGet(() -> cartRepository.save(new Cart(customerId)));
        
        // 檢查是否已有該商品
        boolean itemExists = cart.getItems().stream()
            .anyMatch(item -> item.getProductId().equals(productId));
        
        if (!itemExists) {
            // 創建購物車項目
            CartItem item = new CartItem(productId, quantity, mockProduct.getPrice());
            cart.addItem(item);
            cartItemRepository.save(item);
            cartRepository.save(cart);
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