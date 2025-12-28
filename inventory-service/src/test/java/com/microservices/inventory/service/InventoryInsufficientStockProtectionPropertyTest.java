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
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 庫存不足保護屬性測試
 * Feature: microservices-order-inventory, Property 14: 庫存不足保護
 * 驗證需求: 需求 3.4
 */
class InventoryInsufficientStockProtectionPropertyTest {

    private InventoryRepository inventoryRepository;
    private InventoryReservationRepository reservationRepository;
    private InventoryLockManager lockManager;
    private RestTemplate restTemplate;
    private InventoryService inventoryService;

    @BeforeProperty
    void setUp() {
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        reservationRepository = Mockito.mock(InventoryReservationRepository.class);
        lockManager = Mockito.mock(InventoryLockManager.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        inventoryService = new InventoryServiceImpl(inventoryRepository, reservationRepository, lockManager, restTemplate);
    }

    /**
     * 屬性 14: 庫存不足保護
     * 對於任何庫存不足的預留請求，應該被拒絕並返回庫存不足錯誤
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 14: 庫存不足保護")
    void shouldRejectReservationWhenInsufficientStock(
            @ForAll("validProductIds") Long productId,
            @ForAll("limitedStockQuantities") Integer availableStock,
            @ForAll("excessiveReservationQuantities") Integer requestedQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 確保請求數量大於可用庫存
        Assume.that(requestedQuantity > availableStock);

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體
        Inventory inventory = new Inventory(productId, availableStock, 5);
        inventory.setId(1L);
        inventory.setVersion(0L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductIdWithLock(productId))
                .thenReturn(Optional.of(inventory));

        // 設定預留記錄存儲庫的回應 - 沒有現有預留
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(new ArrayList<>());

        // 設定分散式鎖管理器 - 模擬成功獲取鎖
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        // 執行測試：嘗試預留超過可用庫存的數量
        assertThatThrownBy(() -> {
            inventoryService.reserveTemporary(productId, customerId, requestedQuantity, expiresAt);
        })
        .isInstanceOf(InventoryService.InsufficientStockException.class)
        .hasMessageContaining("庫存不足")
        .hasMessageContaining("產品ID: " + productId)
        .hasMessageContaining("需要數量: " + requestedQuantity)
        .hasMessageContaining("可用庫存: " + availableStock);

        // 驗證庫存沒有被修改
        verify(inventoryRepository, never()).save(any(Inventory.class));
        
        // 驗證沒有創建預留記錄
        verify(reservationRepository, never()).save(any(InventoryReservation.class));
        
        // 驗證庫存查詢被調用
        verify(inventoryRepository).findByProductIdWithLock(productId);
    }

    /**
     * 屬性測試：邊界情況 - 請求數量等於可用庫存應該成功
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 14: 邊界情況測試")
    void shouldAllowReservationWhenExactlyEqualToAvailableStock(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer availableStock,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體
        Inventory inventory = new Inventory(productId, availableStock, 5);
        inventory.setId(1L);
        inventory.setVersion(0L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductIdWithLock(productId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenReturn(inventory);

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(new ArrayList<>());
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

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        // 執行測試：預留等於可用庫存的數量
        InventoryReservation result = inventoryService.reserveTemporary(
                productId, customerId, availableStock, expiresAt);

        // 驗證預留成功
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getQuantity()).isEqualTo(availableStock);
        assertThat(result.getType()).isEqualTo(ReservationType.TEMPORARY);

        // 驗證庫存被正確更新
        verify(inventoryRepository).save(argThat(inv -> 
            inv.getAvailableStock().equals(0) &&
            inv.getTemporaryReserved().equals(availableStock)
        ));

        // 驗證預留記錄被保存
        verify(reservationRepository).save(any(InventoryReservation.class));
    }

    /**
     * 屬性測試：部分庫存不足的情況
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 14: 部分庫存不足保護")
    void shouldRejectReservationWhenPartiallyInsufficientStock(
            @ForAll("validProductIds") Long productId,
            @ForAll("limitedStockQuantities") Integer availableStock,
            @ForAll("validStockQuantities") Integer temporaryReserved,
            @ForAll("validReservationQuantities") Integer requestedQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 確保請求數量大於實際可用庫存（考慮已預留的部分）
        int actualAvailable = Math.max(0, availableStock - temporaryReserved);
        Assume.that(requestedQuantity > actualAvailable);

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體（已有部分預留）
        Inventory inventory = new Inventory(productId, actualAvailable, 5);
        inventory.setId(1L);
        inventory.setTemporaryReserved(temporaryReserved);
        inventory.setVersion(0L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductIdWithLock(productId))
                .thenReturn(Optional.of(inventory));

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(new ArrayList<>());

        // 設定分散式鎖管理器
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        // 執行測試：嘗試預留超過實際可用庫存的數量
        assertThatThrownBy(() -> {
            inventoryService.reserveTemporary(productId, customerId, requestedQuantity, expiresAt);
        })
        .isInstanceOf(InventoryService.InsufficientStockException.class)
        .hasMessageContaining("庫存不足")
        .hasMessageContaining("產品ID: " + productId)
        .hasMessageContaining("需要數量: " + requestedQuantity)
        .hasMessageContaining("可用庫存: " + actualAvailable);

        // 驗證庫存沒有被修改
        verify(inventoryRepository, never()).save(any(Inventory.class));
        
        // 驗證沒有創建預留記錄
        verify(reservationRepository, never()).save(any(InventoryReservation.class));
    }

    /**
     * 屬性測試：調整預留時的庫存不足保護
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 14: 調整預留庫存不足保護")
    void shouldRejectAdjustmentWhenInsufficientStockForIncrease(
            @ForAll("validProductIds") Long productId,
            @ForAll("limitedStockQuantities") Integer availableStock,
            @ForAll("validReservationQuantities") Integer currentReservation,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 計算新的預留數量，確保增加的部分超過可用庫存
        int increaseAmount = availableStock + 1; // 超過可用庫存
        int newReservationQuantity = currentReservation + increaseAmount;

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體
        Inventory inventory = new Inventory(productId, availableStock, 5);
        inventory.setId(1L);
        inventory.setTemporaryReserved(currentReservation);
        inventory.setVersion(0L);

        // 創建現有的臨時預留記錄
        InventoryReservation existingReservation = new InventoryReservation(
                productId, customerId, currentReservation, ReservationType.TEMPORARY, 
                LocalDateTime.now().plusHours(1));
        existingReservation.setId(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductIdWithLock(productId))
                .thenReturn(Optional.of(inventory));

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(java.util.List.of(existingReservation));

        // 設定分散式鎖管理器
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        // 執行測試：嘗試調整預留到超過可用庫存的數量
        assertThatThrownBy(() -> {
            inventoryService.adjustTemporaryReservation(productId, customerId, newReservationQuantity, expiresAt);
        })
        .isInstanceOf(InventoryService.InsufficientStockException.class)
        .hasMessageContaining("庫存不足以增加預留")
        .hasMessageContaining("產品ID: " + productId)
        .hasMessageContaining("需要增加: " + increaseAmount)
        .hasMessageContaining("可用庫存: " + availableStock);

        // 驗證庫存沒有被修改
        verify(inventoryRepository, never()).save(any(Inventory.class));
        
        // 驗證現有預留記錄沒有被刪除
        verify(reservationRepository, never()).delete(any(InventoryReservation.class));
        
        // 驗證沒有創建新的預留記錄
        verify(reservationRepository, never()).save(any(InventoryReservation.class));
    }

    /**
     * 屬性測試：零庫存情況下的保護
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 14: 零庫存保護")
    void shouldRejectAnyReservationWhenZeroStock(
            @ForAll("validProductIds") Long productId,
            @ForAll("validReservationQuantities") Integer requestedQuantity,
            @ForAll("validCustomerIds") String customerId) throws Exception {

        // 確保請求數量大於0
        Assume.that(requestedQuantity > 0);

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建零庫存實體
        Inventory inventory = new Inventory(productId, 0, 5);
        inventory.setId(1L);
        inventory.setVersion(0L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductIdWithLock(productId))
                .thenReturn(Optional.of(inventory));

        // 設定預留記錄存儲庫的回應
        when(reservationRepository.findByProductIdAndCustomerIdAndType(
                productId, customerId, ReservationType.TEMPORARY))
                .thenReturn(new ArrayList<>());

        // 設定分散式鎖管理器
        when(lockManager.executeWithProductLock(eq(productId), any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier = invocation.getArgument(1);
                    return supplier.get();
                });

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        // 執行測試：嘗試在零庫存時預留任何數量
        assertThatThrownBy(() -> {
            inventoryService.reserveTemporary(productId, customerId, requestedQuantity, expiresAt);
        })
        .isInstanceOf(InventoryService.InsufficientStockException.class)
        .hasMessageContaining("庫存不足")
        .hasMessageContaining("產品ID: " + productId)
        .hasMessageContaining("需要數量: " + requestedQuantity)
        .hasMessageContaining("可用庫存: 0");

        // 驗證庫存沒有被修改
        verify(inventoryRepository, never()).save(any(Inventory.class));
        
        // 驗證沒有創建預留記錄
        verify(reservationRepository, never()).save(any(InventoryReservation.class));
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
    Arbitrary<Integer> limitedStockQuantities() {
        return Arbitraries.integers().between(1, 20);
    }

    @Provide
    Arbitrary<Integer> validReservationQuantities() {
        return Arbitraries.integers().between(1, 30);
    }

    @Provide
    Arbitrary<Integer> excessiveReservationQuantities() {
        return Arbitraries.integers().between(21, 100);
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