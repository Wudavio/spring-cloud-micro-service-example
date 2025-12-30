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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 庫存預留原子性屬性測試
 * Feature: microservices-order-inventory, Property 13: 庫存預留原子性
 * 驗證需求: 需求 3.2, 3.3, 3.7
 */
class InventoryReservationAtomicityPropertyTest {

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
     * 屬性 13: 庫存預留原子性
     * 對於任何庫存預留操作，在並發情況下應該保持原子性，確保總預留數量不超過可用庫存
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 13: 庫存預留原子性")
    void shouldMaintainAtomicityInConcurrentReservations(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialStock,
            @ForAll("validReservationQuantities") Integer reservationQuantity,
            @ForAll("validCustomerIds") Long customerId) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體
        Inventory inventory = new Inventory(productId, initialStock, 10);
        inventory.setId(1L);
        inventory.setVersion(0L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductIdWithLock(productId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenReturn(inventory);

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndUserIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(new ArrayList<>());
        when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(invocation -> {
                    InventoryReservation reservation = invocation.getArgument(0);
                    reservation.setId(System.currentTimeMillis()); // 模擬自動生成的ID
                    return reservation;
                });

        // 設定分散式鎖管理器 - 模擬成功獲取鎖
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        if (reservationQuantity <= initialStock) {
            // 如果預留數量不超過可用庫存，操作應該成功
            InventoryReservation result = inventoryService.reserveTemporary(
                    productId, customerId, reservationQuantity, expiresAt);

            // 驗證預留記錄
            assertThat(result).isNotNull();
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getUserId()).isEqualTo(customerId);
            assertThat(result.getQuantity()).isEqualTo(reservationQuantity);
            assertThat(result.getType()).isEqualTo(ReservationType.TEMPORARY);

            // 驗證庫存更新
            verify(inventoryRepository).save(argThat(inv -> 
                inv.getAvailableStock().equals(initialStock - reservationQuantity) &&
                inv.getTemporaryReserved().equals(reservationQuantity)
            ));

            // 驗證預留記錄保存
            verify(reservationRepository).save(any(InventoryReservation.class));
        } else {
            // 如果預留數量超過可用庫存，應該拋出庫存不足異常
            try {
                inventoryService.reserveTemporary(productId, customerId, reservationQuantity, expiresAt);
                // 如果沒有拋出異常，測試失敗
                assertThat(false).as("應該拋出庫存不足異常").isTrue();
            } catch (InventoryService.InsufficientStockException e) {
                // 預期的異常，驗證錯誤訊息
                assertThat(e.getMessage()).contains("庫存不足");
                
                // 驗證沒有保存預留記錄
                verify(reservationRepository, never()).save(any(InventoryReservation.class));
            }
        }
    }

    /**
     * 屬性測試：並發預留操作的原子性
     * 測試多個並發預留操作不會導致超賣
     */
    @Property(tries = 50)
    @Label("Feature: microservices-order-inventory, Property 13: 並發預留原子性")
    void shouldPreventOversellInConcurrentReservations(
            @ForAll("validProductIds") Long productId,
            @ForAll("limitedStockQuantities") Integer initialStock,
            @ForAll("concurrentCustomerCount") Integer customerCount) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體
        Inventory inventory = new Inventory(productId, initialStock, 5);
        inventory.setId(1L);
        AtomicInteger currentStock = new AtomicInteger(initialStock);
        AtomicInteger reservedStock = new AtomicInteger(0);

        // 設定庫存存儲庫的回應 - 模擬樂觀鎖行為
        when(inventoryRepository.findByProductIdWithLock(productId))
                .thenAnswer(invocation -> {
                    Inventory inv = new Inventory(productId, currentStock.get(), 5);
                    inv.setId(1L);
                    inv.setTemporaryReserved(reservedStock.get());
                    inv.setVersion(System.currentTimeMillis()); // 模擬版本號變化
                    return Optional.of(inv);
                });

        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> {
                    Inventory inv = invocation.getArgument(0);
                    // 模擬樂觀鎖檢查
                    if (currentStock.get() < 0) {
                        throw new OptimisticLockingFailureException("樂觀鎖衝突");
                    }
                    currentStock.set(inv.getAvailableStock());
                    reservedStock.set(inv.getTemporaryReserved());
                    return inv;
                });

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndUserIdAndType(
                anyLong(), any(), eq(ReservationType.TEMPORARY)))
                .thenReturn(new ArrayList<>());
        when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(invocation -> {
                    InventoryReservation reservation = invocation.getArgument(0);
                    reservation.setId(System.currentTimeMillis());
                    return reservation;
                });

        // 設定分散式鎖管理器 - 模擬互斥訪問
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    synchronized (this) {
                        return supplier.get();
                    }
                });

        // 創建多個並發預留請求
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        
        for (int i = 0; i < customerCount; i++) {
            Long customerId = (long) (i + 1);
            int reserveQuantity = 1; // 每個客戶預留1個單位
            
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    inventoryService.reserveTemporary(productId, customerId, reserveQuantity, expiresAt);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
            futures.add(future);
        }

        // 等待所有操作完成
        List<Boolean> results = new ArrayList<>();
        for (CompletableFuture<Boolean> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                results.add(false);
            }
        }

        // 計算成功的預留數量
        long successfulReservations = results.stream().mapToLong(success -> success ? 1 : 0).sum();

        // 驗證：成功的預留數量不應該超過初始庫存
        assertThat(successfulReservations).isLessThanOrEqualTo(initialStock);
        
        // 驗證：最終的可用庫存應該是非負數
        assertThat(currentStock.get()).isGreaterThanOrEqualTo(0);
        
        // 驗證：可用庫存 + 預留庫存 = 初始庫存
        assertThat(currentStock.get() + reservedStock.get()).isEqualTo(initialStock);
    }

    /**
     * 屬性測試：確認預留的原子性
     * 測試臨時預留轉換為確認預留的原子性
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 13: 確認預留原子性")
    void shouldMaintainAtomicityInReservationConfirmation(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialStock,
            @ForAll("validReservationQuantities") Integer tempReservedQuantity,
            @ForAll("validReservationQuantities") Integer confirmQuantity,
            @ForAll("validCustomerIds") Long customerId) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體（已有臨時預留）
        Inventory inventory = new Inventory(productId, initialStock - tempReservedQuantity, 10);
        inventory.setId(1L);
        inventory.setTemporaryReserved(tempReservedQuantity);
        inventory.setVersion(0L);

        // 創建臨時預留記錄
        InventoryReservation tempReservation = new InventoryReservation(
                productId, customerId, tempReservedQuantity, ReservationType.TEMPORARY, 
                LocalDateTime.now().plusHours(1));
        tempReservation.setId(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductIdWithLock(productId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenReturn(inventory);

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndUserIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(List.of(tempReservation));
        when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(invocation -> {
                    InventoryReservation reservation = invocation.getArgument(0);
                    reservation.setId(System.currentTimeMillis());
                    return reservation;
                });

        // 設定分散式鎖管理器
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        if (confirmQuantity <= tempReservedQuantity) {
            // 如果確認數量不超過臨時預留數量，操作應該成功
            InventoryReservation result = inventoryService.confirmReservation(
                    productId, customerId, confirmQuantity);

            // 驗證確認預留記錄
            assertThat(result).isNotNull();
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getUserId()).isEqualTo(customerId);
            assertThat(result.getQuantity()).isEqualTo(confirmQuantity);
            assertThat(result.getType()).isEqualTo(ReservationType.CONFIRMED);

            // 驗證庫存更新
            int expectedAvailable = initialStock - tempReservedQuantity;
            if (confirmQuantity < tempReservedQuantity) {
                expectedAvailable += (tempReservedQuantity - confirmQuantity);
            }
            
            final int finalExpectedAvailable = expectedAvailable;
            verify(inventoryRepository).save(argThat(inv -> 
                inv.getAvailableStock().equals(finalExpectedAvailable) &&
                inv.getTemporaryReserved().equals(0) &&
                inv.getConfirmedReserved().equals(confirmQuantity)
            ));

            // 驗證刪除了臨時預留記錄
            verify(reservationRepository).delete(tempReservation);
            
            // 驗證保存了確認預留記錄
            verify(reservationRepository).save(any(InventoryReservation.class));
        } else {
            // 如果確認數量超過臨時預留數量，應該拋出異常
            try {
                inventoryService.confirmReservation(productId, customerId, confirmQuantity);
                // 如果沒有拋出異常，測試失敗
                assertThat(false).as("應該拋出預留不足異常").isTrue();
            } catch (InventoryService.ReservationNotFoundException e) {
                // 預期的異常，驗證錯誤訊息
                assertThat(e.getMessage()).contains("臨時預留數量不足");
            }
        }
    }

    // 資料生成器

    @Provide
    Arbitrary<Long> validProductIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<Integer> validStockQuantities() {
        return Arbitraries.integers().between(10, 1000);
    }

    @Provide
    Arbitrary<Integer> validReservationQuantities() {
        return Arbitraries.integers().between(1, 50);
    }

    @Provide
    Arbitrary<Integer> limitedStockQuantities() {
        return Arbitraries.integers().between(5, 20);
    }

    @Provide
    Arbitrary<Integer> concurrentCustomerCount() {
        return Arbitraries.integers().between(3, 10);
    }

    @Provide
    Arbitrary<Long> validCustomerIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }
}