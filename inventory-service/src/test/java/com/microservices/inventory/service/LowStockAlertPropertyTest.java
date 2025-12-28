package com.microservices.inventory.service;

import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.repository.InventoryRepository;
import com.microservices.inventory.repository.InventoryReservationRepository;
import com.microservices.inventory.service.impl.InventoryServiceImpl;
import com.microservices.inventory.service.impl.LowStockNotificationServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 低庫存告警屬性測試
 * Feature: microservices-order-inventory, Property 17: 低庫存告警
 * 驗證需求: 需求 3.10
 */
class LowStockAlertPropertyTest {

    private InventoryRepository inventoryRepository;
    private InventoryReservationRepository reservationRepository;
    private InventoryLockManager lockManager;
    private RestTemplate restTemplate;
    private InventoryService inventoryService;
    private LowStockNotificationService lowStockNotificationService;

    @BeforeProperty
    void setUp() {
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        reservationRepository = Mockito.mock(InventoryReservationRepository.class);
        lockManager = Mockito.mock(InventoryLockManager.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        inventoryService = new InventoryServiceImpl(inventoryRepository, reservationRepository, lockManager, restTemplate);
        lowStockNotificationService = new LowStockNotificationServiceImpl(inventoryService);
    }

    /**
     * 屬性 17: 低庫存告警
     * 對於任何總庫存水準低於閾值的產品，系統應該發出低庫存通知
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 17: 低庫存告警")
    void shouldSendLowStockAlertForProductsBelowThreshold(
            @ForAll("lowStockInventories") List<Inventory> inventories) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 過濾出真正低庫存的產品（總可用庫存 <= 閾值）
        List<Inventory> expectedLowStockInventories = inventories.stream()
                .filter(inventory -> inventory.getTotalAvailableStock() <= inventory.getLowStockThreshold())
                .collect(Collectors.toList());

        // 設定庫存存儲庫返回所有低庫存產品
        when(inventoryRepository.findLowStockInventories())
                .thenReturn(expectedLowStockInventories);

        // 執行低庫存檢查和告警
        int alertCount = lowStockNotificationService.checkAndSendLowStockAlerts();

        // 驗證告警數量等於低庫存產品數量
        assertThat(alertCount).isEqualTo(expectedLowStockInventories.size());

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findLowStockInventories();

        // 驗證每個低庫存產品都應該觸發告警
        for (Inventory inventory : expectedLowStockInventories) {
            boolean shouldAlert = lowStockNotificationService.shouldSendLowStockAlert(inventory);
            assertThat(shouldAlert).isTrue();
            
            // 驗證庫存確實低於閾值
            assertThat(inventory.getTotalAvailableStock()).isLessThanOrEqualTo(inventory.getLowStockThreshold());
        }
    }

    /**
     * 屬性測試：高庫存產品不應該觸發低庫存告警
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 17: 高庫存產品不觸發告警")
    void shouldNotSendLowStockAlertForProductsAboveThreshold(
            @ForAll("highStockInventories") List<Inventory> inventories) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 確保所有產品都是高庫存（總可用庫存 > 閾值）
        List<Inventory> highStockInventories = inventories.stream()
                .filter(inventory -> inventory.getTotalAvailableStock() > inventory.getLowStockThreshold())
                .collect(Collectors.toList());

        // 設定庫存存儲庫返回空列表（沒有低庫存產品）
        when(inventoryRepository.findLowStockInventories())
                .thenReturn(Arrays.asList());

        // 執行低庫存檢查和告警
        int alertCount = lowStockNotificationService.checkAndSendLowStockAlerts();

        // 驗證沒有告警被發送
        assertThat(alertCount).isEqualTo(0);

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findLowStockInventories();

        // 驗證每個高庫存產品都不應該觸發告警
        for (Inventory inventory : highStockInventories) {
            boolean shouldAlert = lowStockNotificationService.shouldSendLowStockAlert(inventory);
            assertThat(shouldAlert).isFalse();
            
            // 驗證庫存確實高於閾值
            assertThat(inventory.getTotalAvailableStock()).isGreaterThan(inventory.getLowStockThreshold());
        }
    }

    /**
     * 屬性測試：邊界情況 - 庫存等於閾值應該觸發告警
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 17: 邊界情況告警")
    void shouldSendLowStockAlertForProductsAtThreshold(
            @ForAll("validProductIds") Long productId,
            @ForAll("validThresholds") Integer threshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存等於閾值的產品
        Inventory inventory = new Inventory(productId, threshold, threshold);
        inventory.setId(System.currentTimeMillis());
        inventory.setTemporaryReserved(0);
        inventory.setConfirmedReserved(0);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory.setVersion(1L);

        // 設定庫存存儲庫返回這個邊界情況的產品
        when(inventoryRepository.findLowStockInventories())
                .thenReturn(Arrays.asList(inventory));

        // 執行低庫存檢查和告警
        int alertCount = lowStockNotificationService.checkAndSendLowStockAlerts();

        // 驗證告警被發送
        assertThat(alertCount).isEqualTo(1);

        // 驗證邊界情況應該觸發告警
        boolean shouldAlert = lowStockNotificationService.shouldSendLowStockAlert(inventory);
        assertThat(shouldAlert).isTrue();

        // 驗證庫存等於閾值
        assertThat(inventory.getTotalAvailableStock()).isEqualTo(inventory.getLowStockThreshold());

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findLowStockInventories();
    }

    /**
     * 屬性測試：零庫存產品應該觸發告警
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 17: 零庫存告警")
    void shouldSendLowStockAlertForZeroStockProducts(
            @ForAll("validProductIds") Long productId,
            @ForAll("validThresholds") Integer threshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建零庫存產品
        Inventory inventory = new Inventory(productId, 0, threshold);
        inventory.setId(System.currentTimeMillis());
        inventory.setTemporaryReserved(0);
        inventory.setConfirmedReserved(0);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory.setVersion(1L);

        // 設定庫存存儲庫返回零庫存產品
        when(inventoryRepository.findLowStockInventories())
                .thenReturn(Arrays.asList(inventory));

        // 執行低庫存檢查和告警
        int alertCount = lowStockNotificationService.checkAndSendLowStockAlerts();

        // 驗證告警被發送
        assertThat(alertCount).isEqualTo(1);

        // 驗證零庫存應該觸發告警
        boolean shouldAlert = lowStockNotificationService.shouldSendLowStockAlert(inventory);
        assertThat(shouldAlert).isTrue();

        // 驗證庫存為零
        assertThat(inventory.getTotalAvailableStock()).isEqualTo(0);
        assertThat(inventory.getTotalAvailableStock()).isLessThanOrEqualTo(inventory.getLowStockThreshold());

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findLowStockInventories();
    }

    /**
     * 屬性測試：批量低庫存告警的正確性
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 17: 批量低庫存告警")
    void shouldSendBatchLowStockAlertsCorrectly(
            @ForAll("mixedStockInventories") List<Inventory> inventories) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 分離低庫存和高庫存產品
        List<Inventory> lowStockInventories = inventories.stream()
                .filter(inventory -> inventory.getTotalAvailableStock() <= inventory.getLowStockThreshold())
                .collect(Collectors.toList());

        List<Inventory> highStockInventories = inventories.stream()
                .filter(inventory -> inventory.getTotalAvailableStock() > inventory.getLowStockThreshold())
                .collect(Collectors.toList());

        // 設定庫存存儲庫只返回低庫存產品
        when(inventoryRepository.findLowStockInventories())
                .thenReturn(lowStockInventories);

        // 執行低庫存檢查和告警
        int alertCount = lowStockNotificationService.checkAndSendLowStockAlerts();

        // 驗證告警數量等於低庫存產品數量
        assertThat(alertCount).isEqualTo(lowStockInventories.size());

        // 驗證批量告警功能
        int batchAlertCount = lowStockNotificationService.sendBatchLowStockAlerts(lowStockInventories);
        assertThat(batchAlertCount).isEqualTo(lowStockInventories.size());

        // 驗證每個低庫存產品都應該觸發告警
        for (Inventory inventory : lowStockInventories) {
            boolean shouldAlert = lowStockNotificationService.shouldSendLowStockAlert(inventory);
            assertThat(shouldAlert).isTrue();
        }

        // 驗證每個高庫存產品都不應該觸發告警
        for (Inventory inventory : highStockInventories) {
            boolean shouldAlert = lowStockNotificationService.shouldSendLowStockAlert(inventory);
            assertThat(shouldAlert).isFalse();
        }

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findLowStockInventories();
    }

    /**
     * 屬性測試：空庫存列表不應該觸發告警
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 17: 空庫存列表處理")
    void shouldHandleEmptyInventoryListCorrectly() {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 設定庫存存儲庫返回空列表
        when(inventoryRepository.findLowStockInventories())
                .thenReturn(Arrays.asList());

        // 執行低庫存檢查和告警
        int alertCount = lowStockNotificationService.checkAndSendLowStockAlerts();

        // 驗證沒有告警被發送
        assertThat(alertCount).isEqualTo(0);

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findLowStockInventories();
    }

    /**
     * 屬性測試：單個產品低庫存告警
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 17: 單個產品告警")
    void shouldSendAlertForSingleLowStockProduct(
            @ForAll("validProductIds") Long productId,
            @ForAll("lowStockQuantities") Integer availableStock,
            @ForAll("validThresholds") Integer threshold) {

        // 確保庫存低於閾值
        Assume.that(availableStock <= threshold);

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建低庫存產品
        Inventory inventory = new Inventory(productId, availableStock, threshold);
        inventory.setId(System.currentTimeMillis());
        inventory.setTemporaryReserved(0);
        inventory.setConfirmedReserved(0);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory.setVersion(1L);

        // 直接測試單個產品告警
        boolean shouldAlert = lowStockNotificationService.shouldSendLowStockAlert(inventory);
        assertThat(shouldAlert).isTrue();

        // 驗證庫存確實低於或等於閾值
        assertThat(inventory.getTotalAvailableStock()).isLessThanOrEqualTo(inventory.getLowStockThreshold());

        // 測試發送告警（不會拋出異常）
        lowStockNotificationService.sendLowStockAlert(inventory);
    }

    // 資料生成器

    @Provide
    Arbitrary<Long> validProductIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Integer> validThresholds() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<Integer> lowStockQuantities() {
        return Arbitraries.integers().between(0, 50);
    }

    @Provide
    Arbitrary<Integer> highStockQuantities() {
        return Arbitraries.integers().between(101, 1000);
    }

    @Provide
    Arbitrary<List<Inventory>> lowStockInventories() {
        // 生成低庫存產品列表
        Arbitrary<Inventory> lowStockInventory = Arbitraries.longs().between(1L, 1000L).flatMap(productId ->
            Arbitraries.integers().between(1, 50).flatMap(threshold ->
                Arbitraries.integers().between(0, threshold).map(availableStock -> {
                    Inventory inventory = new Inventory(productId, availableStock, threshold);
                    inventory.setId(productId);
                    inventory.setTemporaryReserved(0);
                    inventory.setConfirmedReserved(0);
                    inventory.setUpdatedAt(LocalDateTime.now());
                    inventory.setVersion(1L);
                    return inventory;
                })
            )
        );
        return lowStockInventory.list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<Inventory>> highStockInventories() {
        // 生成高庫存產品列表
        Arbitrary<Inventory> highStockInventory = Arbitraries.longs().between(1L, 1000L).flatMap(productId ->
            Arbitraries.integers().between(1, 50).flatMap(threshold ->
                Arbitraries.integers().between(threshold + 1, threshold + 500).map(availableStock -> {
                    Inventory inventory = new Inventory(productId, availableStock, threshold);
                    inventory.setId(productId);
                    inventory.setTemporaryReserved(0);
                    inventory.setConfirmedReserved(0);
                    inventory.setUpdatedAt(LocalDateTime.now());
                    inventory.setVersion(1L);
                    return inventory;
                })
            )
        );
        return highStockInventory.list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<Inventory>> mixedStockInventories() {
        // 生成混合庫存產品列表（包含低庫存和高庫存）
        Arbitrary<Inventory> mixedStockInventory = Arbitraries.longs().between(1L, 1000L).flatMap(productId ->
            Arbitraries.integers().between(1, 50).flatMap(threshold ->
                Arbitraries.integers().between(0, threshold + 500).map(availableStock -> {
                    Inventory inventory = new Inventory(productId, availableStock, threshold);
                    inventory.setId(productId);
                    inventory.setTemporaryReserved(0);
                    inventory.setConfirmedReserved(0);
                    inventory.setUpdatedAt(LocalDateTime.now());
                    inventory.setVersion(1L);
                    return inventory;
                })
            )
        );
        return mixedStockInventory.list().ofMinSize(2).ofMaxSize(15);
    }
}