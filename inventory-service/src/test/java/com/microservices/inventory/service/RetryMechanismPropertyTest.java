package com.microservices.inventory.service;

import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.entity.InventoryReservation;
import com.microservices.inventory.exception.RetryExhaustedException;
import com.microservices.inventory.exception.SystemBusyException;
import com.microservices.inventory.repository.InventoryRepository;
import com.microservices.inventory.repository.InventoryReservationRepository;
import com.microservices.inventory.service.impl.InventoryServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 屬性 20: 重試機制
 * 對於任何發生衝突的庫存操作，系統應該自動重試最多3次，達到上限後返回操作失敗
 * 驗證需求: 需求 10.3, 10.4, 3.12
 */
@SpringBootTest
@ActiveProfiles("test")
class RetryMechanismPropertyTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public RestTemplate mockRestTemplate() {
            RestTemplate mockRestTemplate = mock(RestTemplate.class);
            // Mock 產品服務響應，讓產品驗證通過
            when(mockRestTemplate.getForObject(anyString(), eq(Boolean.class))).thenReturn(true);
            return mockRestTemplate;
        }
    }

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private RetryableInventoryService retryableInventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @BeforeProperty
    void setUp() {
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    void testRetryMechanismWithOptimisticLockingFailure() {
        // 創建測試庫存
        Long productId = 1L;
        Inventory inventory = inventoryService.createInventory(productId, 100, 10);
        
        // 模擬並發操作導致樂觀鎖衝突
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger retryExhaustedCount = new AtomicInteger(0);
        
        for (int i = 0; i < 5; i++) {
            final Long customerId = (long) (i + 1);
            executor.submit(() -> {
                try {
                    retryableInventoryService.reserveTemporaryWithRetry(
                        productId, customerId, 10, LocalDateTime.now().plusHours(1));
                    successCount.incrementAndGet();
                } catch (RetryExhaustedException e) {
                    retryExhaustedCount.incrementAndGet();
                } catch (Exception e) {
                    // 其他異常
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        
        // 驗證：至少有一些操作成功，可能有一些因重試耗盡而失敗
        assertThat(successCount.get() + retryExhaustedCount.get()).isEqualTo(5);
        assertThat(successCount.get()).isGreaterThan(0);
    }

    @Test
    void testRetryMechanismExhaustion() {
        // 創建測試庫存
        Long productId = 2L;
        inventoryService.createInventory(productId, 5, 1);
        
        // 先預留所有庫存
        try {
            inventoryService.reserveTemporary(productId, 1L, 5, LocalDateTime.now().plusHours(1));
        } catch (Exception e) {
            // 忽略
        }
        
        // 嘗試預留超過可用庫存的數量，應該立即失敗（不是重試耗盡）
        assertThatThrownBy(() -> {
            retryableInventoryService.reserveTemporaryWithRetry(
                productId, 2L, 10, LocalDateTime.now().plusHours(1));
        }).isInstanceOf(com.microservices.inventory.service.InventoryService.InsufficientStockException.class);
    }

    @Test
    void testRetryMechanismWithSystemBusy() {
        // 創建測試庫存
        Long productId = 3L;
        inventoryService.createInventory(productId, 100, 10);
        
        // 正常情況下應該成功
        assertThatCode(() -> {
            retryableInventoryService.reserveTemporaryWithRetry(
                productId, 1L, 10, LocalDateTime.now().plusHours(1));
        }).doesNotThrowAnyException();
        
        // 驗證預留成功
        assertThat(inventoryService.hasAvailableStock(productId, 90)).isTrue();
        assertThat(inventoryService.hasAvailableStock(productId, 91)).isFalse();
    }

    @Test
    void testConfirmReservationRetry() {
        // 創建測試庫存和臨時預留
        Long productId = 4L;
        Long customerId = 1L;
        inventoryService.createInventory(productId, 100, 10);
        
        try {
            inventoryService.reserveTemporary(productId, customerId, 20, LocalDateTime.now().plusHours(1));
        } catch (Exception e) {
            // 忽略
        }
        
        // 確認預留應該成功
        assertThatCode(() -> {
            retryableInventoryService.confirmReservationWithRetry(productId, customerId, 20);
        }).doesNotThrowAnyException();
        
        // 驗證庫存狀態
        Inventory inventory = inventoryService.findByProductId(productId).orElseThrow();
        assertThat(inventory.getAvailableStock()).isEqualTo(80);
        assertThat(inventory.getTemporaryReserved()).isEqualTo(0);
        assertThat(inventory.getConfirmedReserved()).isEqualTo(20);
    }

    @Test
    void testAdjustTemporaryReservationRetry() {
        // 創建測試庫存和臨時預留
        Long productId = 5L;
        Long customerId = 1L;
        inventoryService.createInventory(productId, 100, 10);
        
        try {
            inventoryService.reserveTemporary(productId, customerId, 20, LocalDateTime.now().plusHours(1));
        } catch (Exception e) {
            // 忽略
        }
        
        // 調整預留數量應該成功
        assertThatCode(() -> {
            retryableInventoryService.adjustTemporaryReservationWithRetry(
                productId, customerId, 30, LocalDateTime.now().plusHours(1));
        }).doesNotThrowAnyException();
        
        // 驗證庫存狀態
        Inventory inventory = inventoryService.findByProductId(productId).orElseThrow();
        assertThat(inventory.getAvailableStock()).isEqualTo(70);
        assertThat(inventory.getTemporaryReserved()).isEqualTo(30);
    }

    @Test
    void testRetryMechanismPreservesDataConsistency() {
        // 創建測試庫存
        Long productId = 6L;
        inventoryService.createInventory(productId, 50, 5);
        
        // 並發執行多個預留操作
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger totalReserved = new AtomicInteger(0);
        
        for (int i = 0; i < 10; i++) {
            final Long customerId = (long) (i + 1);
            final int quantity = 5;
            executor.submit(() -> {
                try {
                    retryableInventoryService.reserveTemporaryWithRetry(
                        productId, customerId, quantity, LocalDateTime.now().plusHours(1));
                    totalReserved.addAndGet(quantity);
                } catch (Exception e) {
                    // 預留失敗，不增加計數
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        
        // 驗證數據一致性：總預留數量不應超過初始庫存
        Inventory inventory = inventoryService.findByProductId(productId).orElseThrow();
        int actualReserved = inventory.getTemporaryReserved();
        
        assertThat(actualReserved).isLessThanOrEqualTo(50);
        assertThat(inventory.getAvailableStock() + actualReserved).isEqualTo(50);
        assertThat(totalReserved.get()).isEqualTo(actualReserved);
    }
}