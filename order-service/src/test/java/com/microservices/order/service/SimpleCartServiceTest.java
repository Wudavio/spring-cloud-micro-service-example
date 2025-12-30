package com.microservices.order.service;

import com.microservices.order.TestOrderServiceApplication;
import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.dto.CartDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 簡單的購物車服務測試，用於驗證配置
 */
@SpringBootTest(classes = TestOrderServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class SimpleCartServiceTest {
    
    @Autowired
    private CartService cartService;
    
    @MockBean
    private ProductServiceClient productServiceClient;
    
    @MockBean
    private InventoryServiceClient inventoryServiceClient;
    
    @Test
    void testCartServiceIsNotNull() {
        assertThat(cartService).isNotNull();
        assertThat(productServiceClient).isNotNull();
        assertThat(inventoryServiceClient).isNotNull();
    }
    
    @Test
    void testAddToCartWithMockedServices() {
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = new ProductServiceClient.ProductDTO();
        mockProduct.setId(1L);
        mockProduct.setName("測試產品");
        mockProduct.setPrice(BigDecimal.valueOf(100.00));
        mockProduct.setStatus("ACTIVE");
        when(productServiceClient.getProduct(1L)).thenReturn(mockProduct);
        
        // 模擬庫存預留成功
        InventoryServiceClient.ReservationDTO mockReservation = new InventoryServiceClient.ReservationDTO();
        mockReservation.setId(1L);
        mockReservation.setProductId(1L);
        mockReservation.setUserId("1");
        mockReservation.setQuantity(2);
        mockReservation.setType("TEMPORARY");
        when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(mockReservation);
        
        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest(1L, 2);
        request.setUserId(1L);
        
        // 執行添加操作
        CartDTO result = cartService.addToCart(request);
        
        // 驗證結果
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProductId()).isEqualTo(1L);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
    }
}