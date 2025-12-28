package com.microservices.order.service;

import com.microservices.order.TestOrderServiceApplication;
import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.dto.CartDTO;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 重試機制屬性測試
 * 驗證需求: 需求 10.3, 10.4, 3.12
 */
@SpringBootTest(classes = TestOrderServiceApplication.class)
@ActiveProfiles("test")
class RetryMechanismPropertyTest {

    @Autowired
    private CartService cartService;

    @MockBean
    private ProductServiceClient productServiceClient;

    @MockBean
    private InventoryServiceClient inventoryServiceClient;

    @Test
    void testBasicRetryMechanism() {
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
        mockReservation.setUserId("customer1");
        mockReservation.setQuantity(2);
        mockReservation.setType("TEMPORARY");
        when(inventoryServiceClient.reserveInventory(anyLong(), any())).thenReturn(mockReservation);

        // 創建添加到購物車的請求
        AddToCartRequest request = new AddToCartRequest("customer1", 1L, 2);

        // 執行添加操作
        CartDTO result = cartService.addToCart(request);

        // 驗證結果
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("customer1");
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void testRetryOnFeignException() {
        // 模擬產品存在且狀態為 ACTIVE
        ProductServiceClient.ProductDTO mockProduct = new ProductServiceClient.ProductDTO();
        mockProduct.setId(1L);
        mockProduct.setName("測試產品");
        mockProduct.setPrice(BigDecimal.valueOf(100.00));
        mockProduct.setStatus("ACTIVE");
        when(productServiceClient.getProduct(1L)).thenReturn(mockProduct);

        // 模擬庫存服務暫時不可用，然後成功
        Request request = Request.create(Request.HttpMethod.POST, "/test", Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(inventoryServiceClient.reserveInventory(anyLong(), any()))
                .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", request, null, Collections.emptyMap()))
                .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", request, null, Collections.emptyMap()))
                .thenReturn(createMockReservation());

        // 創建添加到購物車的請求
        AddToCartRequest addRequest = new AddToCartRequest("customer1", 1L, 2);

        // 執行添加操作 - 應該在重試後成功
        CartDTO result = cartService.addToCart(addRequest);

        // 驗證結果
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("customer1");
    }

    private InventoryServiceClient.ReservationDTO createMockReservation() {
        InventoryServiceClient.ReservationDTO reservation = new InventoryServiceClient.ReservationDTO();
        reservation.setId(1L);
        reservation.setProductId(1L);
        reservation.setUserId("customer1");
        reservation.setQuantity(2);
        reservation.setType("TEMPORARY");
        return reservation;
    }
}