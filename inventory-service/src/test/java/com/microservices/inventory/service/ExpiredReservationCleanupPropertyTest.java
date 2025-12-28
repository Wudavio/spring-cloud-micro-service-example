package com.microservices.inventory.service;

import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.entity.InventoryReservation;
import com.microservices.inventory.entity.ReservationType;
import com.microservices.inventory.repository.InventoryRepository;
import com.microservices.inventory.repository.InventoryReservationRepository;
import com.microservices.inventory.service.impl.InventoryServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 過期預留清理屬性測試
 * Feature: microservices-order-inventory, Property 18: 過期預留清理
 * 驗證需求: 需求 3.11
 */
class ExpiredReservationCleanupPropertyTest {

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
     * 屬性 18: 過期預留清理
     * 對於任何超過設定時間的臨時預留，系統應該自動釋放這些過期預留
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 18: 過期預留清理")
    void shouldCleanupExpiredTemporaryReservations(
            @ForAll("expiredReservationsWithInventory") ExpiredReservationTestData testData) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate, environment);
        
        // 設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        List<InventoryReservation> expiredReservations = testData.expiredReservations;
        List<Inventory> inventories = testData.inventories;

        // 設定 mock 行為
        when(reservationRepository.findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class)))
                .thenReturn(expiredReservations);

        // 為每個庫存設定查詢行為
        for (Inventory inventory : inventories) {
            when(inventoryRepository.findByProductId(inventory.getProductId()))
                    .thenReturn(Optional.of(inventory));
        }

        // 設定鎖管理器行為 - 成功執行所有操作
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(lockManager).executeWithProductLock(any(Long.class), any(Runnable.class));

        // 執行過期預留清理
        int cleanedCount = inventoryService.cleanupExpiredTemporaryReservations();

        // 驗證清理數量等於過期預留數量
        assertThat(cleanedCount).isEqualTo(expiredReservations.size());

        // 驗證查詢過期預留的方法被調用
        verify(reservationRepository).findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class));

        // 驗證每個過期預留都被處理
        for (InventoryReservation expiredReservation : expiredReservations) {
            // 驗證庫存查詢被調用（可能多次調用同一產品）
            verify(inventoryRepository, atLeastOnce()).findByProductId(expiredReservation.getProductId());
            
            // 驗證預留記錄被刪除
            verify(reservationRepository).delete(expiredReservation);
        }

        // 驗證分散式鎖被使用（可能多次調用同一產品）
        for (InventoryReservation expiredReservation : expiredReservations) {
            verify(lockManager, atLeastOnce()).executeWithProductLock(eq(expiredReservation.getProductId()), any(Runnable.class));
        }

        // 驗證庫存更新被調用（每個相關的庫存至少一次）
        for (Inventory inventory : inventories) {
            // 檢查是否有預留對應這個庫存
            boolean hasReservationForThisInventory = expiredReservations.stream()
                    .anyMatch(r -> r.getProductId().equals(inventory.getProductId()));
            if (hasReservationForThisInventory) {
                verify(inventoryRepository, atLeastOnce()).save(any(Inventory.class));
            }
        }
    }

    /**
     * 屬性測試：沒有過期預留時不應該執行任何清理操作
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 18: 無過期預留處理")
    void shouldNotCleanupWhenNoExpiredReservations() throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate, environment);
        
        // 設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        // 設定沒有過期預留
        when(reservationRepository.findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());

        // 執行過期預留清理
        int cleanedCount = inventoryService.cleanupExpiredTemporaryReservations();

        // 驗證沒有清理任何記錄
        assertThat(cleanedCount).isEqualTo(0);

        // 驗證查詢方法被調用
        verify(reservationRepository).findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class));

        // 驗證沒有其他操作被執行
        verify(inventoryRepository, never()).findByProductId(any(Long.class));
        verify(reservationRepository, never()).delete(any(InventoryReservation.class));
        verify(lockManager, never()).executeWithProductLock(any(Long.class), any(Runnable.class));
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    /**
     * 屬性測試：只清理臨時預留，不清理確認預留
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 18: 只清理臨時預留")
    void shouldOnlyCleanupTemporaryReservations(
            @ForAll("mixedExpiredReservationsWithInventory") MixedReservationTestData testData) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate, environment);
        
        // 設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        List<InventoryReservation> expiredTemporaryReservations = testData.expiredTemporaryReservations;
        List<InventoryReservation> expiredConfirmedReservations = testData.expiredConfirmedReservations;
        List<Inventory> inventories = testData.inventories;

        // 設定只返回過期的臨時預留
        when(reservationRepository.findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class)))
                .thenReturn(expiredTemporaryReservations);

        // 為每個庫存設定查詢行為
        for (Inventory inventory : inventories) {
            when(inventoryRepository.findByProductId(inventory.getProductId()))
                    .thenReturn(Optional.of(inventory));
        }

        // 設定鎖管理器行為
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(lockManager).executeWithProductLock(any(Long.class), any(Runnable.class));

        // 執行過期預留清理
        int cleanedCount = inventoryService.cleanupExpiredTemporaryReservations();

        // 驗證只清理臨時預留
        assertThat(cleanedCount).isEqualTo(expiredTemporaryReservations.size());

        // 驗證只查詢臨時預留
        verify(reservationRepository).findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class));
        verify(reservationRepository, never()).findByTypeAndExpiresAtBefore(eq(ReservationType.CONFIRMED), any(LocalDateTime.class));

        // 驗證只刪除臨時預留
        for (InventoryReservation tempReservation : expiredTemporaryReservations) {
            verify(reservationRepository).delete(tempReservation);
        }

        // 驗證確認預留沒有被刪除
        for (InventoryReservation confirmedReservation : expiredConfirmedReservations) {
            verify(reservationRepository, never()).delete(confirmedReservation);
        }
    }

    /**
     * 屬性測試：庫存釋放的正確性
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 18: 庫存釋放正確性")
    void shouldCorrectlyReleaseInventoryWhenCleaningExpiredReservations(
            @ForAll("singleExpiredReservationWithInventory") SingleReservationTestData testData) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate, environment);
        
        // 設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        InventoryReservation expiredReservation = testData.expiredReservation;
        Inventory originalInventory = testData.inventory;

        // 記錄原始庫存狀態
        Integer originalAvailableStock = originalInventory.getAvailableStock();
        Integer originalTemporaryReserved = originalInventory.getTemporaryReserved();
        Integer reservationQuantity = expiredReservation.getQuantity();

        // 設定 mock 行為
        when(reservationRepository.findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(expiredReservation));

        when(inventoryRepository.findByProductId(originalInventory.getProductId()))
                .thenReturn(Optional.of(originalInventory));

        // 設定鎖管理器行為
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(lockManager).executeWithProductLock(any(Long.class), any(Runnable.class));

        // 執行過期預留清理
        int cleanedCount = inventoryService.cleanupExpiredTemporaryReservations();

        // 驗證清理了一個預留
        assertThat(cleanedCount).isEqualTo(1);

        // 驗證庫存狀態被正確更新
        assertThat(originalInventory.getAvailableStock())
                .isEqualTo(originalAvailableStock + reservationQuantity);
        assertThat(originalInventory.getTemporaryReserved())
                .isEqualTo(originalTemporaryReserved - reservationQuantity);

        // 驗證庫存保存被調用
        verify(inventoryRepository).save(originalInventory);

        // 驗證預留記錄被刪除
        verify(reservationRepository).delete(expiredReservation);
    }

    /**
     * 屬性測試：處理庫存不存在的情況
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 18: 處理庫存不存在")
    void shouldHandleNonExistentInventoryGracefully(
            @ForAll("expiredReservationsOnly") List<InventoryReservation> expiredReservations) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate, environment);
        
        // 設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        // 設定過期預留存在但庫存不存在
        when(reservationRepository.findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class)))
                .thenReturn(expiredReservations);

        // 設定所有庫存查詢都返回空
        when(inventoryRepository.findByProductId(any(Long.class)))
                .thenReturn(Optional.empty());

        // 設定鎖管理器行為
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(lockManager).executeWithProductLock(any(Long.class), any(Runnable.class));

        // 執行過期預留清理
        int cleanedCount = inventoryService.cleanupExpiredTemporaryReservations();

        // 驗證仍然清理了預留記錄（即使庫存不存在）
        assertThat(cleanedCount).isEqualTo(expiredReservations.size());

        // 驗證預留記錄被刪除
        for (InventoryReservation reservation : expiredReservations) {
            verify(reservationRepository).delete(reservation);
        }

        // 驗證沒有庫存更新操作
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    /**
     * 屬性測試：批量清理的正確性
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 18: 批量清理正確性")
    void shouldCorrectlyHandleBatchCleanup(
            @ForAll("multipleExpiredReservationsWithInventory") MultipleReservationTestData testData) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate, environment);
        
        // 設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        List<InventoryReservation> expiredReservations = testData.expiredReservations;
        List<Inventory> inventories = testData.inventories;

        // 設定 mock 行為
        when(reservationRepository.findByTypeAndExpiresAtBefore(eq(ReservationType.TEMPORARY), any(LocalDateTime.class)))
                .thenReturn(expiredReservations);

        // 為每個庫存設定查詢行為
        for (Inventory inventory : inventories) {
            when(inventoryRepository.findByProductId(inventory.getProductId()))
                    .thenReturn(Optional.of(inventory));
        }

        // 設定鎖管理器行為
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(lockManager).executeWithProductLock(any(Long.class), any(Runnable.class));

        // 執行過期預留清理
        int cleanedCount = inventoryService.cleanupExpiredTemporaryReservations();

        // 驗證清理數量正確
        assertThat(cleanedCount).isEqualTo(expiredReservations.size());

        // 驗證每個過期預留都被處理
        for (InventoryReservation reservation : expiredReservations) {
            verify(reservationRepository).delete(reservation);
        }

        // 驗證分散式鎖被使用（可能多次調用同一產品）
        for (InventoryReservation reservation : expiredReservations) {
            verify(lockManager, atLeastOnce()).executeWithProductLock(eq(reservation.getProductId()), any(Runnable.class));
        }

        // 驗證庫存更新次數正確（每個相關庫存至少被保存一次）
        for (Inventory inventory : inventories) {
            // 檢查是否有預留對應這個庫存
            boolean hasReservationForThisInventory = expiredReservations.stream()
                    .anyMatch(r -> r.getProductId().equals(inventory.getProductId()));
            if (hasReservationForThisInventory) {
                verify(inventoryRepository, atLeastOnce()).save(any(Inventory.class));
            }
        }

        // 計算總的預留數量釋放
        int totalReleasedQuantity = expiredReservations.stream()
                .mapToInt(InventoryReservation::getQuantity)
                .sum();

        assertThat(totalReleasedQuantity).isGreaterThan(0);
    }

    // 資料生成器和輔助類別

    /**
     * 測試資料類別：包含過期預留和對應庫存
     */
    static class ExpiredReservationTestData {
        final List<InventoryReservation> expiredReservations;
        final List<Inventory> inventories;

        ExpiredReservationTestData(List<InventoryReservation> expiredReservations, List<Inventory> inventories) {
            this.expiredReservations = expiredReservations;
            this.inventories = inventories;
        }
    }

    /**
     * 測試資料類別：包含混合類型的過期預留
     */
    static class MixedReservationTestData {
        final List<InventoryReservation> expiredTemporaryReservations;
        final List<InventoryReservation> expiredConfirmedReservations;
        final List<Inventory> inventories;

        MixedReservationTestData(List<InventoryReservation> expiredTemporaryReservations,
                               List<InventoryReservation> expiredConfirmedReservations,
                               List<Inventory> inventories) {
            this.expiredTemporaryReservations = expiredTemporaryReservations;
            this.expiredConfirmedReservations = expiredConfirmedReservations;
            this.inventories = inventories;
        }
    }

    /**
     * 測試資料類別：單個過期預留和庫存
     */
    static class SingleReservationTestData {
        final InventoryReservation expiredReservation;
        final Inventory inventory;

        SingleReservationTestData(InventoryReservation expiredReservation, Inventory inventory) {
            this.expiredReservation = expiredReservation;
            this.inventory = inventory;
        }
    }

    /**
     * 測試資料類別：多個過期預留和庫存
     */
    static class MultipleReservationTestData {
        final List<InventoryReservation> expiredReservations;
        final List<Inventory> inventories;

        MultipleReservationTestData(List<InventoryReservation> expiredReservations, List<Inventory> inventories) {
            this.expiredReservations = expiredReservations;
            this.inventories = inventories;
        }
    }

    @Provide
    Arbitrary<Long> validProductIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> validCustomerIds() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(20);
    }

    @Provide
    Arbitrary<Integer> validQuantities() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<Integer> validStockAmounts() {
        return Arbitraries.integers().between(50, 1000);
    }

    @Provide
    Arbitrary<LocalDateTime> expiredTimes() {
        // 生成過期時間（過去的時間）
        return Arbitraries.longs().between(1, 86400) // 1秒到1天前
                .map(secondsAgo -> LocalDateTime.now().minusSeconds(secondsAgo));
    }

    @Provide
    Arbitrary<InventoryReservation> expiredTemporaryReservation() {
        return validProductIds().flatMap(productId ->
            validCustomerIds().flatMap(customerId ->
                validQuantities().flatMap(quantity ->
                    expiredTimes().map(expiresAt -> {
                        InventoryReservation reservation = new InventoryReservation(
                                productId, customerId, quantity, ReservationType.TEMPORARY, expiresAt);
                        reservation.setId(System.currentTimeMillis() + productId);
                        reservation.setCreatedAt(expiresAt.minusHours(1));
                        return reservation;
                    })
                )
            )
        );
    }

    @Provide
    Arbitrary<InventoryReservation> expiredConfirmedReservation() {
        return validProductIds().flatMap(productId ->
            validCustomerIds().flatMap(customerId ->
                validQuantities().flatMap(quantity ->
                    expiredTimes().map(expiresAt -> {
                        InventoryReservation reservation = new InventoryReservation(
                                productId, customerId, quantity, ReservationType.CONFIRMED, expiresAt);
                        reservation.setId(System.currentTimeMillis() + productId + 10000);
                        reservation.setCreatedAt(expiresAt.minusHours(1));
                        return reservation;
                    })
                )
            )
        );
    }

    @Provide
    Arbitrary<List<InventoryReservation>> expiredReservationsOnly() {
        return expiredTemporaryReservation().list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Inventory> inventoryForProduct() {
        return validProductIds().flatMap(productId ->
            validStockAmounts().flatMap(availableStock ->
                validQuantities().map(temporaryReserved -> {
                    Inventory inventory = new Inventory(productId, availableStock, 10);
                    inventory.setId(productId);
                    inventory.setTemporaryReserved(temporaryReserved);
                    inventory.setConfirmedReserved(0);
                    inventory.setUpdatedAt(LocalDateTime.now());
                    inventory.setVersion(1L);
                    return inventory;
                })
            )
        );
    }

    @Provide
    Arbitrary<ExpiredReservationTestData> expiredReservationsWithInventory() {
        return expiredReservationsOnly().flatMap(reservations ->
            inventoryForProduct().list().ofMinSize(1).ofMaxSize(5).map(inventories -> {
                // 確保每個預留都有對應的庫存
                for (int i = 0; i < reservations.size() && i < inventories.size(); i++) {
                    reservations.get(i).setProductId(inventories.get(i).getProductId());
                }
                return new ExpiredReservationTestData(reservations, inventories);
            })
        );
    }

    @Provide
    Arbitrary<MixedReservationTestData> mixedExpiredReservationsWithInventory() {
        return expiredTemporaryReservation().list().ofMinSize(1).ofMaxSize(3).flatMap(tempReservations ->
            expiredConfirmedReservation().list().ofMinSize(1).ofMaxSize(3).flatMap(confirmedReservations ->
                inventoryForProduct().list().ofMinSize(1).ofMaxSize(5).map(inventories -> {
                    // 確保預留有對應的庫存
                    for (int i = 0; i < tempReservations.size() && i < inventories.size(); i++) {
                        tempReservations.get(i).setProductId(inventories.get(i).getProductId());
                    }
                    return new MixedReservationTestData(tempReservations, confirmedReservations, inventories);
                })
            )
        );
    }

    @Provide
    Arbitrary<SingleReservationTestData> singleExpiredReservationWithInventory() {
        return expiredTemporaryReservation().flatMap(reservation ->
            inventoryForProduct().map(inventory -> {
                // 確保預留和庫存是同一個產品
                reservation.setProductId(inventory.getProductId());
                // 確保庫存有足夠的臨時預留數量
                inventory.setTemporaryReserved(reservation.getQuantity());
                return new SingleReservationTestData(reservation, inventory);
            })
        );
    }

    @Provide
    Arbitrary<MultipleReservationTestData> multipleExpiredReservationsWithInventory() {
        return expiredTemporaryReservation().list().ofMinSize(2).ofMaxSize(5).flatMap(reservations ->
            inventoryForProduct().list().ofMinSize(1).ofMaxSize(3).map(inventories -> {
                // 將預留分配給庫存
                for (int i = 0; i < reservations.size(); i++) {
                    Inventory inventory = inventories.get(i % inventories.size());
                    reservations.get(i).setProductId(inventory.getProductId());
                }
                
                // 計算每個庫存的總預留數量
                for (Inventory inventory : inventories) {
                    int totalReserved = reservations.stream()
                            .filter(r -> r.getProductId().equals(inventory.getProductId()))
                            .mapToInt(InventoryReservation::getQuantity)
                            .sum();
                    inventory.setTemporaryReserved(totalReserved);
                }
                
                return new MultipleReservationTestData(reservations, inventories);
            })
        );
    }
}