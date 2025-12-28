package com.microservices.inventory.service;

import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.repository.InventoryRepository;
import com.microservices.inventory.repository.InventoryReservationRepository;
import com.microservices.inventory.service.impl.InventoryServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 庫存創建驗證屬性測試
 * Feature: microservices-order-inventory, Property 12: 庫存創建驗證
 * 驗證需求: 需求 3.1
 */
class InventoryCreationValidationPropertyTest {

    private InventoryRepository inventoryRepository;
    private InventoryReservationRepository reservationRepository;
    private InventoryLockManager lockManager;
    private RestTemplate restTemplate;
    private Environment environment;
    private InventoryService inventoryService;

    @BeforeProperty
    void setUp() {
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        reservationRepository = Mockito.mock(InventoryReservationRepository.class);
        lockManager = Mockito.mock(InventoryLockManager.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        environment = Mockito.mock(Environment.class);
        
        // 設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        
        inventoryService = new InventoryServiceImpl(inventoryRepository, reservationRepository, lockManager, restTemplate, environment);
    }

    /**
     * 屬性 12: 庫存創建驗證
     * 對於任何庫存創建請求，只有在產品服務中存在的產品才能成功創建庫存記錄
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 12: 庫存創建驗證")
    void shouldOnlyCreateInventoryForExistingProducts(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialStock,
            @ForAll("validThresholds") Integer lowStockThreshold,
            @ForAll boolean productExists) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 設定產品服務的回應
        when(restTemplate.getForObject(anyString(), eq(Boolean.class)))
                .thenReturn(productExists);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.existsByProductId(productId)).thenReturn(false);
        
        if (productExists) {
            // 如果產品存在，模擬成功保存庫存
            Inventory expectedInventory = new Inventory(productId, initialStock, lowStockThreshold);
            when(inventoryRepository.save(any(Inventory.class))).thenReturn(expectedInventory);

            // 執行創建庫存操作
            Inventory result = inventoryService.createInventory(productId, initialStock, lowStockThreshold);

            // 驗證結果
            assertThat(result).isNotNull();
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getAvailableStock()).isEqualTo(initialStock);
            assertThat(result.getLowStockThreshold()).isEqualTo(lowStockThreshold);
            assertThat(result.getTemporaryReserved()).isEqualTo(0);
            assertThat(result.getConfirmedReserved()).isEqualTo(0);

            // 驗證調用了產品服務驗證
            verify(restTemplate).getForObject(anyString(), eq(Boolean.class));
            // 驗證保存了庫存記錄
            verify(inventoryRepository).save(any(Inventory.class));
        } else {
            // 如果產品不存在，應該拋出異常
            assertThatThrownBy(() -> inventoryService.createInventory(productId, initialStock, lowStockThreshold))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("產品不存在，無法創建庫存記錄");

            // 驗證調用了產品服務驗證
            verify(restTemplate).getForObject(anyString(), eq(Boolean.class));
            // 驗證沒有保存庫存記錄
            verify(inventoryRepository, never()).save(any(Inventory.class));
        }
    }

    /**
     * 屬性測試：重複創建相同產品的庫存應該失敗
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 12: 重複創建庫存驗證")
    void shouldRejectDuplicateInventoryCreation(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialStock,
            @ForAll("validThresholds") Integer lowStockThreshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 設定產品存在
        when(restTemplate.getForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        // 設定庫存記錄已存在
        when(inventoryRepository.existsByProductId(productId)).thenReturn(true);

        // 嘗試創建庫存應該失敗
        assertThatThrownBy(() -> inventoryService.createInventory(productId, initialStock, lowStockThreshold))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("該產品已存在庫存記錄");

        // 驗證沒有保存庫存記錄
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    /**
     * 屬性測試：無效參數應該被拒絕
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 12: 無效參數驗證")
    void shouldRejectInvalidParameters(
            @ForAll("validProductIds") Long productId,
            @ForAll("invalidStockQuantities") Integer invalidStock,
            @ForAll("invalidThresholds") Integer invalidThreshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 設定產品存在且庫存記錄不存在
        when(restTemplate.getForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(inventoryRepository.existsByProductId(productId)).thenReturn(false);

        // 測試無效的初始庫存
        if (invalidStock < 0) {
            assertThatThrownBy(() -> inventoryService.createInventory(productId, invalidStock, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("初始庫存不能為負數");
        }

        // 測試無效的低庫存閾值
        if (invalidThreshold < 0) {
            assertThatThrownBy(() -> inventoryService.createInventory(productId, 100, invalidThreshold))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("低庫存閾值不能為負數");
        }

        // 驗證沒有保存庫存記錄
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    // 資料生成器

    @Provide
    Arbitrary<Long> validProductIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Integer> validStockQuantities() {
        return Arbitraries.integers().between(0, 10000);
    }

    @Provide
    Arbitrary<Integer> validThresholds() {
        return Arbitraries.integers().between(0, 1000);
    }

    @Provide
    Arbitrary<Integer> invalidStockQuantities() {
        return Arbitraries.integers().between(-1000, -1);
    }

    @Provide
    Arbitrary<Integer> invalidThresholds() {
        return Arbitraries.integers().between(-1000, -1);
    }
}