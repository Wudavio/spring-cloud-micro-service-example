package com.microservices.order.service;

import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.OrderDTO;
import com.microservices.order.dto.OrderItemDTO;
import com.microservices.order.dto.PlaceOrderRequest;
import com.microservices.order.entity.Cart;
import com.microservices.order.entity.CartItem;
import com.microservices.order.entity.OrderStatus;
import com.microservices.order.exception.CartNotFoundException;
import com.microservices.order.repository.CartRepository;
import com.microservices.order.repository.CartItemRepository;
import com.microservices.order.repository.OrderRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;

/**
 * 購物車到訂單轉換屬性測試
 * Feature: microservices-order-inventory, Property 5: 購物車到訂單轉換
 * 驗證需求: 需求 1.7, 1.8
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CartToOrderConversionPropertyTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private CartRepository cartRepository;
    
    @Autowired
    private CartItemRepository cartItemRepository;
    
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
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        
        // 重置 Mock
        reset(productServiceClient, inventoryServiceClient);
    }
    
    /**
     * 屬性 5: 購物車到訂單轉換 - 有效購物車應該成功轉換為訂單
     */
    @Property(tries = 100)
    @Label("購物車到訂單轉換 - 有效購物車應該成功轉換為訂單")
    void validCartShouldBeConvertedToOrderSuccessfully(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 5) Integer itemCount) {
        
        // 創建購物車和項目
        Cart cart = setupCartWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務和庫存服務調用
        mockServicesForOrderCreation(cart.getItems());
        
        // 計算預期總金額
        BigDecimal expectedTotalAmount = cart.getItems().stream()
            .map(CartItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 執行下單
        PlaceOrderRequest request = new PlaceOrderRequest(customerId);
        OrderDTO result = orderService.placeOrder(request);
        
        // 驗證訂單基本資訊
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getOrderNumber()).isNotNull().startsWith("ORD");
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(expectedTotalAmount);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        
        // 驗證訂單項目
        assertThat(result.getItems()).hasSize(itemCount);
        
        for (int i = 0; i < result.getItems().size(); i++) {
            OrderItemDTO orderItem = result.getItems().get(i);
            CartItem cartItem = cart.getItems().get(i);
            
            assertThat(orderItem.getId()).isNotNull();
            assertThat(orderItem.getProductId()).isEqualTo(cartItem.getProductId());
            assertThat(orderItem.getQuantity()).isEqualTo(cartItem.getQuantity());
            assertThat(orderItem.getUnitPrice()).isEqualByComparingTo(cartItem.getUnitPrice());
            assertThat(orderItem.getTotalPrice()).isEqualByComparingTo(cartItem.getTotalPrice());
            assertThat(orderItem.getProductName()).isNotNull();
        }
        
        // 驗證購物車已被清空
        assertThat(cartRepository.findByCustomerId(customerId)).isEmpty();
        
        // 驗證庫存確認調用
        verify(inventoryServiceClient, times(itemCount)).confirmReservation(anyLong(), any());
    }
    
    /**
     * 屬性 5: 購物車到訂單轉換 - 空購物車應該拋出異常
     */
    @Property(tries = 100)
    @Label("購物車到訂單轉換 - 空購物車應該拋出異常")
    void emptyCartShouldThrowException(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId) {
        
        // 不創建購物車或創建空購物車
        PlaceOrderRequest request = new PlaceOrderRequest(customerId);
        
        // 驗證拋出 CartNotFoundException
        assertThatThrownBy(() -> orderService.placeOrder(request))
            .isInstanceOf(CartNotFoundException.class)
            .hasMessageContaining("購物車為空或不存在");
        
        // 驗證沒有創建訂單
        assertThat(orderRepository.findByCustomerId(customerId, null)).isEmpty();
    }
    
    /**
     * 屬性 5: 購物車到訂單轉換 - 庫存確認失敗應該拋出異常
     */
    @Property(tries = 50)
    @Label("購物車到訂單轉換 - 庫存確認失敗應該拋出異常")
    void inventoryConfirmationFailureShouldThrowException(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 3) Integer itemCount) {
        
        // 創建購物車和項目
        Cart cart = setupCartWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務成功，但庫存確認失敗
        mockProductServiceForAllItems(cart.getItems());
        doThrow(new RuntimeException("Inventory confirmation failed"))
            .when(inventoryServiceClient).confirmReservation(anyLong(), any(InventoryServiceClient.ConfirmReservationRequest.class));
        
        // 執行下單
        PlaceOrderRequest request = new PlaceOrderRequest(customerId);
        
        // 驗證拋出 RuntimeException
        assertThatThrownBy(() -> orderService.placeOrder(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("庫存確認失敗");
        
        // 驗證購物車仍然存在（事務回滾）
        assertThat(cartRepository.findByCustomerId(customerId)).isPresent();
    }
    
    /**
     * 屬性 5: 購物車到訂單轉換 - 訂單號應該是唯一的
     */
    @Property(tries = 50)
    @Label("購物車到訂單轉換 - 訂單號應該是唯一的")
    void orderNumbersShouldBeUnique(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId1,
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId2) {
        
        Assume.that(!customerId1.equals(customerId2));
        
        // 為兩個客戶創建購物車
        Cart cart1 = setupCartWithMultipleItems(customerId1, 2);
        Cart cart2 = setupCartWithMultipleItems(customerId2, 2);
        
        // 模擬服務調用
        mockServicesForOrderCreation(cart1.getItems());
        mockServicesForOrderCreation(cart2.getItems());
        
        // 執行兩次下單
        PlaceOrderRequest request1 = new PlaceOrderRequest(customerId1);
        PlaceOrderRequest request2 = new PlaceOrderRequest(customerId2);
        
        OrderDTO order1 = orderService.placeOrder(request1);
        OrderDTO order2 = orderService.placeOrder(request2);
        
        // 驗證訂單號唯一
        assertThat(order1.getOrderNumber()).isNotEqualTo(order2.getOrderNumber());
        assertThat(order1.getId()).isNotEqualTo(order2.getId());
    }
    
    /**
     * 屬性 5: 購物車到訂單轉換 - 轉換過程應該是原子性的
     */
    @Property(tries = 50)
    @Label("購物車到訂單轉換 - 轉換過程應該是原子性的")
    void conversionShouldBeAtomic(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 2, max = 4) Integer itemCount) {
        
        // 創建購物車和項目
        Cart cart = setupCartWithMultipleItems(customerId, itemCount);
        
        // 模擬產品服務成功
        mockProductServiceForAllItems(cart.getItems());
        
        // 模擬第一個商品庫存確認成功，第二個失敗
        doNothing().when(inventoryServiceClient).confirmReservation(eq(1L), any());
        doThrow(new RuntimeException("Inventory confirmation failed"))
            .when(inventoryServiceClient).confirmReservation(eq(2L), any());
        
        // 執行下單
        PlaceOrderRequest request = new PlaceOrderRequest(customerId);
        
        // 驗證拋出異常
        assertThatThrownBy(() -> orderService.placeOrder(request))
            .isInstanceOf(RuntimeException.class);
        
        // 驗證原子性：購物車應該仍然存在（事務回滾）
        assertThat(cartRepository.findByCustomerId(customerId)).isPresent();
        
        // 驗證沒有創建訂單
        assertThat(orderRepository.findByCustomerId(customerId, null)).isEmpty();
    }
    
    /**
     * 屬性 5: 購物車到訂單轉換 - 產品資訊應該正確映射到訂單項目
     */
    @Property(tries = 100)
    @Label("購物車到訂單轉換 - 產品資訊應該正確映射到訂單項目")
    void productInfoShouldBeMappedCorrectlyToOrderItems(
            @ForAll @StringLength(min = 5, max = 20) @AlphaChars String customerId,
            @ForAll @IntRange(min = 1, max = 3) Integer itemCount) {
        
        // 創建購物車和項目
        Cart cart = setupCartWithMultipleItems(customerId, itemCount);
        
        // 模擬服務調用
        mockServicesForOrderCreation(cart.getItems());
        
        // 執行下單
        PlaceOrderRequest request = new PlaceOrderRequest(customerId);
        OrderDTO result = orderService.placeOrder(request);
        
        // 驗證每個訂單項目的產品資訊映射
        for (int i = 0; i < result.getItems().size(); i++) {
            OrderItemDTO orderItem = result.getItems().get(i);
            CartItem cartItem = cart.getItems().get(i);
            
            // 驗證數量和價格映射
            assertThat(orderItem.getQuantity()).isEqualTo(cartItem.getQuantity());
            assertThat(orderItem.getUnitPrice()).isEqualByComparingTo(cartItem.getUnitPrice());
            
            // 驗證總價計算
            BigDecimal expectedTotalPrice = cartItem.getUnitPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            assertThat(orderItem.getTotalPrice()).isEqualByComparingTo(expectedTotalPrice);
            
            // 驗證產品名稱已被填充
            assertThat(orderItem.getProductName()).isEqualTo("測試產品 " + cartItem.getProductId());
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
     * 為訂單創建模擬所有服務調用
     */
    private void mockServicesForOrderCreation(List<CartItem> items) {
        mockProductServiceForAllItems(items);
        mockInventoryServiceForAllItems(items);
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
     * 為所有購物車項目模擬庫存服務調用
     */
    private void mockInventoryServiceForAllItems(List<CartItem> items) {
        for (CartItem item : items) {
            doNothing().when(inventoryServiceClient).confirmReservation(eq(item.getProductId()), any());
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