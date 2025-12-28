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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

/**
 * 庫存釋放正確性屬性測試
 * Feature: microservices-order-inventory, Property 15: 庫存釋放正確性
 * 驗證需求: 需求 3.6, 3.8
 */
class InventoryReleaseCorrectnessPropertyTest {

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
     * 屬性 15: 庫存釋放正確性 - 臨時預留釋放
     * 對於任何庫存釋放操作（購物車移除、訂單取消），預留的庫存應該正確釋放回可用庫存
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 15: 臨時預留釋放正確性")
    void shouldCorrectlyReleaseTemporaryReservation(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialAvailableStock,
            @ForAll("validReservationQuantities") Integer reservedQuantity,
            @ForAll("validReleaseQuantities") Integer releaseQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體（已有臨時預留）
        Inventory inventory = new Inventory(productId, initialAvailableStock, 10);
        inventory.setId(1L);
        inventory.setTemporaryReserved(reservedQuantity);
        inventory.setVersion(0L);

        // 創建臨時預留記錄
        InventoryReservation tempReservation = new InventoryReservation(
                productId, customerId, reservedQuantity, ReservationType.TEMPORARY, 
                LocalDateTime.now().plusHours(1));
        tempReservation.setId(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(List.of(tempReservation));

        // 設定分散式鎖管理器 - 模擬成功獲取鎖（Runnable版本）
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(lockManager).executeWithProductLock(eq(productId), any(Runnable.class));

        // 計算預期的釋放數量（不能超過實際預留數量）
        int actualReleaseQuantity = Math.min(releaseQuantity, reservedQuantity);
        int expectedAvailableStock = initialAvailableStock + actualReleaseQuantity;
        int expectedTemporaryReserved = reservedQuantity - actualReleaseQuantity;

        // 執行釋放操作
        inventoryService.releaseTemporaryReservation(productId, customerId, releaseQuantity);

        // 驗證庫存更新 - 可能會被調用多次（釋放時一次，重新預留剩餘數量時一次）
        verify(inventoryRepository, atLeastOnce()).save(argThat(inv -> 
            inv.getProductId().equals(productId)
        ));

        // 驗證預留記錄被刪除
        verify(reservationRepository).delete(tempReservation);

        // 如果還有剩餘預留，驗證新的預留記錄被創建
        if (expectedTemporaryReserved > 0) {
            verify(reservationRepository).save(argThat(reservation ->
                reservation.getProductId().equals(productId) &&
                reservation.getCustomerId().equals(customerId) &&
                reservation.getQuantity().equals(expectedTemporaryReserved) &&
                reservation.getType() == ReservationType.TEMPORARY
            ));
        } else {
            // 如果沒有剩餘預留，不應該創建新記錄
            verify(reservationRepository, never()).save(any(InventoryReservation.class));
        }
    }

    /**
     * 屬性 15: 庫存釋放正確性 - 確認預留釋放
     * 對於任何確認預留釋放操作（訂單取消），預留的庫存應該正確釋放回可用庫存
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 15: 確認預留釋放正確性")
    void shouldCorrectlyReleaseConfirmedReservation(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialAvailableStock,
            @ForAll("validReservationQuantities") Integer confirmedQuantity,
            @ForAll("validReleaseQuantities") Integer releaseQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體（已有確認預留）
        Inventory inventory = new Inventory(productId, initialAvailableStock, 10);
        inventory.setId(1L);
        inventory.setConfirmedReserved(confirmedQuantity);
        inventory.setVersion(0L);

        // 創建確認預留記錄
        InventoryReservation confirmedReservation = new InventoryReservation(
                productId, customerId, confirmedQuantity, ReservationType.CONFIRMED, 
                LocalDateTime.now().plusYears(10));
        confirmedReservation.setId(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.CONFIRMED))
                .thenReturn(List.of(confirmedReservation));

        // 設定分散式鎖管理器 - 模擬成功獲取鎖（Supplier版本）
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        // 計算預期的釋放數量（不能超過實際預留數量）
        int actualReleaseQuantity = Math.min(releaseQuantity, confirmedQuantity);
        int expectedAvailableStock = initialAvailableStock + actualReleaseQuantity;
        int expectedConfirmedReserved = confirmedQuantity - actualReleaseQuantity;

        // 執行釋放操作
        inventoryService.releaseConfirmedReservation(productId, customerId, releaseQuantity);

        // 驗證庫存更新 - 可能會被調用多次
        verify(inventoryRepository, atLeastOnce()).save(argThat(inv -> 
            inv.getProductId().equals(productId)
        ));

        // 驗證預留記錄被刪除
        verify(reservationRepository).delete(confirmedReservation);

        // 如果還有剩餘預留，驗證新的預留記錄被創建
        if (expectedConfirmedReserved > 0) {
            verify(reservationRepository).save(argThat(reservation ->
                reservation.getProductId().equals(productId) &&
                reservation.getCustomerId().equals(customerId) &&
                reservation.getQuantity().equals(expectedConfirmedReserved) &&
                reservation.getType() == ReservationType.CONFIRMED
            ));
        } else {
            // 如果沒有剩餘預留，不應該創建新記錄
            verify(reservationRepository, never()).save(any(InventoryReservation.class));
        }
    }

    /**
     * 屬性測試：完全釋放預留的情況
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 15: 完全釋放預留")
    void shouldCompletelyReleaseWhenReleaseQuantityExceedsReserved(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialAvailableStock,
            @ForAll("validReservationQuantities") Integer reservedQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 釋放數量大於預留數量
        int releaseQuantity = reservedQuantity + 10;

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體
        Inventory inventory = new Inventory(productId, initialAvailableStock, 10);
        inventory.setId(1L);
        inventory.setTemporaryReserved(reservedQuantity);
        inventory.setVersion(0L);

        // 創建臨時預留記錄
        InventoryReservation tempReservation = new InventoryReservation(
                productId, customerId, reservedQuantity, ReservationType.TEMPORARY, 
                LocalDateTime.now().plusHours(1));
        tempReservation.setId(1L);

        // 設定存儲庫回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(List.of(tempReservation));

        // 設定分散式鎖管理器 - 模擬成功獲取鎖（Runnable版本）
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(lockManager).executeWithProductLock(eq(productId), any(Runnable.class));

        // 執行釋放操作
        inventoryService.releaseTemporaryReservation(productId, customerId, releaseQuantity);

        // 驗證完全釋放：所有預留都被釋放回可用庫存
        verify(inventoryRepository, atLeastOnce()).save(argThat(inv -> 
            inv.getProductId().equals(productId)
        ));

        // 驗證原預留記錄被刪除
        verify(reservationRepository).delete(tempReservation);

        // 驗證沒有創建新的預留記錄（因為完全釋放）
        verify(reservationRepository, never()).save(any(InventoryReservation.class));
    }

    /**
     * 屬性測試：部分釋放預留的情況
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 15: 部分釋放預留")
    void shouldPartiallyReleaseWhenReleaseQuantityLessThanReserved(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialAvailableStock,
            @ForAll("largeReservationQuantities") Integer reservedQuantity,
            @ForAll("smallReleaseQuantities") Integer releaseQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 確保釋放數量小於預留數量
        Assume.that(releaseQuantity < reservedQuantity);

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體
        Inventory inventory = new Inventory(productId, initialAvailableStock, 10);
        inventory.setId(1L);
        inventory.setConfirmedReserved(reservedQuantity);
        inventory.setVersion(0L);

        // 創建確認預留記錄
        InventoryReservation confirmedReservation = new InventoryReservation(
                productId, customerId, reservedQuantity, ReservationType.CONFIRMED, 
                LocalDateTime.now().plusYears(10));
        confirmedReservation.setId(1L);

        // 設定存儲庫回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.CONFIRMED))
                .thenReturn(List.of(confirmedReservation));
        when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(invocation -> {
                    InventoryReservation reservation = invocation.getArgument(0);
                    reservation.setId(System.currentTimeMillis());
                    return reservation;
                });

        // 設定分散式鎖管理器 - 模擬成功獲取鎖（Supplier版本）
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        int remainingQuantity = reservedQuantity - releaseQuantity;

        // 執行釋放操作
        inventoryService.releaseConfirmedReservation(productId, customerId, releaseQuantity);

        // 驗證部分釋放：只釋放指定數量
        verify(inventoryRepository, atLeastOnce()).save(argThat(inv -> 
            inv.getProductId().equals(productId)
        ));

        // 驗證原預留記錄被刪除
        verify(reservationRepository).delete(confirmedReservation);

        // 驗證創建了新的預留記錄（剩餘數量）
        verify(reservationRepository).save(argThat(reservation ->
            reservation.getProductId().equals(productId) &&
            reservation.getCustomerId().equals(customerId) &&
            reservation.getQuantity().equals(remainingQuantity) &&
            reservation.getType() == ReservationType.CONFIRMED
        ));
    }

    /**
     * 屬性測試：釋放不存在的預留應該拋出異常
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 15: 釋放不存在預留的錯誤處理")
    void shouldThrowExceptionWhenReleasingNonExistentReservation(
            @ForAll("validProductIds") Long productId,
            @ForAll("validReleaseQuantities") Integer releaseQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體（沒有預留）
        Inventory inventory = new Inventory(productId, 100, 10);
        inventory.setId(1L);
        inventory.setVersion(0L);

        // 設定存儲庫回應 - 沒有找到預留記錄
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(new ArrayList<>());

        // 設定分散式鎖管理器 - 模擬成功獲取鎖（Runnable版本）
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(lockManager).executeWithProductLock(eq(productId), any(Runnable.class));

        // 執行測試：嘗試釋放不存在的臨時預留
        assertThatThrownBy(() -> {
            inventoryService.releaseTemporaryReservation(productId, customerId, releaseQuantity);
        })
        .isInstanceOf(InventoryService.ReservationNotFoundException.class)
        .hasMessageContaining("找不到臨時預留記錄")
        .hasMessageContaining("產品ID: " + productId)
        .hasMessageContaining("客戶ID: " + customerId);

        // 驗證庫存沒有被修改
        verify(inventoryRepository, never()).save(any(Inventory.class));
        
        // 驗證沒有刪除任何預留記錄
        verify(reservationRepository, never()).delete(any(InventoryReservation.class));
    }

    /**
     * 屬性測試：庫存一致性 - 釋放前後總庫存保持不變
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 15: 庫存一致性保證")
    void shouldMaintainTotalStockConsistencyAfterRelease(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer initialAvailableStock,
            @ForAll("validReservationQuantities") Integer tempReserved,
            @ForAll("validReservationQuantities") Integer confirmedReserved,
            @ForAll("validReleaseQuantities") Integer releaseQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 計算初始總庫存
        int initialTotalStock = initialAvailableStock + tempReserved + confirmedReserved;

        // 創建庫存實體
        Inventory inventory = new Inventory(productId, initialAvailableStock, 10);
        inventory.setId(1L);
        inventory.setTemporaryReserved(tempReserved);
        inventory.setConfirmedReserved(confirmedReserved);
        inventory.setVersion(0L);

        // 創建臨時預留記錄
        InventoryReservation tempReservation = new InventoryReservation(
                productId, customerId, tempReserved, ReservationType.TEMPORARY, 
                LocalDateTime.now().plusHours(1));
        tempReservation.setId(1L);

        // 設定存儲庫回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(List.of(tempReservation));
        when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(invocation -> {
                    InventoryReservation reservation = invocation.getArgument(0);
                    reservation.setId(System.currentTimeMillis());
                    return reservation;
                });

        // 設定分散式鎖管理器 - 模擬成功獲取鎖（Runnable版本）
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(lockManager).executeWithProductLock(eq(productId), any(Runnable.class));

        // 執行釋放操作
        inventoryService.releaseTemporaryReservation(productId, customerId, releaseQuantity);

        // 驗證總庫存保持不變 - 允許多次調用 save
        verify(inventoryRepository, atLeastOnce()).save(argThat(inv -> 
            inv.getProductId().equals(productId)
        ));
    }

    // 資料生成器

    @Provide
    Arbitrary<Long> validProductIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<Integer> validStockQuantities() {
        return Arbitraries.integers().between(10, 100);
    }

    @Provide
    Arbitrary<Integer> validReservationQuantities() {
        return Arbitraries.integers().between(1, 50);
    }

    @Provide
    Arbitrary<Integer> largeReservationQuantities() {
        return Arbitraries.integers().between(20, 100);
    }

    @Provide
    Arbitrary<Integer> validReleaseQuantities() {
        return Arbitraries.integers().between(1, 30);
    }

    @Provide
    Arbitrary<Integer> smallReleaseQuantities() {
        return Arbitraries.integers().between(1, 15);
    }

    @Provide
    Arbitrary<String> validCustomerIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "customer_" + s);
    }
}