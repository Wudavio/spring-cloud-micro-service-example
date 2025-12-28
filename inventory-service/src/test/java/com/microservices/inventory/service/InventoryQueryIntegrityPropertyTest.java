package com.microservices.inventory.service;

import com.microservices.inventory.dto.InventoryDTO;
import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.mapper.InventoryMapper;
import com.microservices.inventory.repository.InventoryRepository;
import com.microservices.inventory.repository.InventoryReservationRepository;
import com.microservices.inventory.service.impl.InventoryServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 庫存查詢完整性屬性測試
 * Feature: microservices-order-inventory, Property 16: 庫存查詢完整性
 * 驗證需求: 需求 3.9
 */
class InventoryQueryIntegrityPropertyTest {

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
     * 屬性 16: 庫存查詢完整性
     * 對於任何庫存查詢請求，應該返回可用、臨時預留和正式預留的完整數量資訊
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 16: 庫存查詢完整性")
    void shouldReturnCompleteInventoryInformationForAnyQuery(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer availableStock,
            @ForAll("validReservationQuantities") Integer temporaryReserved,
            @ForAll("validReservationQuantities") Integer confirmedReserved,
            @ForAll("validThresholds") Integer lowStockThreshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建庫存實體，包含所有必要的庫存資訊
        Inventory inventory = new Inventory(productId, availableStock, lowStockThreshold);
        inventory.setId(System.currentTimeMillis());
        inventory.setTemporaryReserved(temporaryReserved);
        inventory.setConfirmedReserved(confirmedReserved);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory.setVersion(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));

        // 執行庫存查詢
        Optional<Inventory> result = inventoryService.findByProductId(productId);

        // 驗證查詢結果存在
        assertThat(result).isPresent();
        
        Inventory foundInventory = result.get();
        
        // 驗證所有必要的庫存資訊都存在且正確
        assertThat(foundInventory.getProductId()).isEqualTo(productId);
        assertThat(foundInventory.getAvailableStock()).isEqualTo(availableStock);
        assertThat(foundInventory.getTemporaryReserved()).isEqualTo(temporaryReserved);
        assertThat(foundInventory.getConfirmedReserved()).isEqualTo(confirmedReserved);
        assertThat(foundInventory.getLowStockThreshold()).isEqualTo(lowStockThreshold);
        assertThat(foundInventory.getUpdatedAt()).isNotNull();
        assertThat(foundInventory.getId()).isNotNull();

        // 驗證 DTO 轉換後的完整性
        InventoryDTO inventoryDTO = InventoryMapper.toDTO(foundInventory);
        assertThat(inventoryDTO).isNotNull();
        assertThat(inventoryDTO.getProductId()).isEqualTo(productId);
        assertThat(inventoryDTO.getAvailableStock()).isEqualTo(availableStock);
        assertThat(inventoryDTO.getTemporaryReserved()).isEqualTo(temporaryReserved);
        assertThat(inventoryDTO.getConfirmedReserved()).isEqualTo(confirmedReserved);
        assertThat(inventoryDTO.getLowStockThreshold()).isEqualTo(lowStockThreshold);
        assertThat(inventoryDTO.getUpdatedAt()).isNotNull();
        assertThat(inventoryDTO.getId()).isNotNull();

        // 驗證計算欄位的正確性
        Integer expectedTotalStock = availableStock + temporaryReserved + confirmedReserved;
        assertThat(inventoryDTO.getTotalStock()).isEqualTo(expectedTotalStock);
        
        Boolean expectedIsLowStock = expectedTotalStock <= lowStockThreshold;
        assertThat(inventoryDTO.getIsLowStock()).isEqualTo(expectedIsLowStock);

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findByProductId(productId);
    }

    /**
     * 屬性測試：不存在的產品查詢應該返回空結果
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 16: 不存在產品查詢處理")
    void shouldReturnEmptyForNonExistentProduct(
            @ForAll("validProductIds") Long productId) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 設定庫存存儲庫返回空結果
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.empty());

        // 執行庫存查詢
        Optional<Inventory> result = inventoryService.findByProductId(productId);

        // 驗證查詢結果為空
        assertThat(result).isEmpty();

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findByProductId(productId);
    }

    /**
     * 屬性測試：零值庫存查詢的完整性
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 16: 零值庫存查詢完整性")
    void shouldReturnCompleteInformationForZeroStockInventory(
            @ForAll("validProductIds") Long productId,
            @ForAll("validThresholds") Integer lowStockThreshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建零庫存實體
        Inventory inventory = new Inventory(productId, 0, lowStockThreshold);
        inventory.setId(System.currentTimeMillis());
        inventory.setTemporaryReserved(0);
        inventory.setConfirmedReserved(0);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory.setVersion(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));

        // 執行庫存查詢
        Optional<Inventory> result = inventoryService.findByProductId(productId);

        // 驗證查詢結果存在
        assertThat(result).isPresent();
        
        Inventory foundInventory = result.get();
        
        // 驗證零值庫存的所有資訊都正確返回
        assertThat(foundInventory.getProductId()).isEqualTo(productId);
        assertThat(foundInventory.getAvailableStock()).isEqualTo(0);
        assertThat(foundInventory.getTemporaryReserved()).isEqualTo(0);
        assertThat(foundInventory.getConfirmedReserved()).isEqualTo(0);
        assertThat(foundInventory.getLowStockThreshold()).isEqualTo(lowStockThreshold);

        // 驗證 DTO 轉換的正確性
        InventoryDTO inventoryDTO = InventoryMapper.toDTO(foundInventory);
        assertThat(inventoryDTO.getTotalStock()).isEqualTo(0);
        assertThat(inventoryDTO.getIsLowStock()).isTrue(); // 零庫存應該被標記為低庫存

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findByProductId(productId);
    }

    /**
     * 屬性測試：高庫存情況下的查詢完整性
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 16: 高庫存查詢完整性")
    void shouldReturnCompleteInformationForHighStockInventory(
            @ForAll("validProductIds") Long productId,
            @ForAll("highStockQuantities") Integer availableStock,
            @ForAll("highReservationQuantities") Integer temporaryReserved,
            @ForAll("highReservationQuantities") Integer confirmedReserved,
            @ForAll("validThresholds") Integer lowStockThreshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建高庫存實體
        Inventory inventory = new Inventory(productId, availableStock, lowStockThreshold);
        inventory.setId(System.currentTimeMillis());
        inventory.setTemporaryReserved(temporaryReserved);
        inventory.setConfirmedReserved(confirmedReserved);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory.setVersion(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));

        // 執行庫存查詢
        Optional<Inventory> result = inventoryService.findByProductId(productId);

        // 驗證查詢結果存在
        assertThat(result).isPresent();
        
        Inventory foundInventory = result.get();
        
        // 驗證高庫存的所有資訊都正確返回
        assertThat(foundInventory.getProductId()).isEqualTo(productId);
        assertThat(foundInventory.getAvailableStock()).isEqualTo(availableStock);
        assertThat(foundInventory.getTemporaryReserved()).isEqualTo(temporaryReserved);
        assertThat(foundInventory.getConfirmedReserved()).isEqualTo(confirmedReserved);
        assertThat(foundInventory.getLowStockThreshold()).isEqualTo(lowStockThreshold);

        // 驗證 DTO 轉換的正確性
        InventoryDTO inventoryDTO = InventoryMapper.toDTO(foundInventory);
        Integer expectedTotalStock = availableStock + temporaryReserved + confirmedReserved;
        assertThat(inventoryDTO.getTotalStock()).isEqualTo(expectedTotalStock);
        
        // 高庫存通常不會是低庫存狀態
        Boolean expectedIsLowStock = expectedTotalStock <= lowStockThreshold;
        assertThat(inventoryDTO.getIsLowStock()).isEqualTo(expectedIsLowStock);

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findByProductId(productId);
    }

    /**
     * 屬性測試：邊界情況 - 庫存等於閾值的查詢完整性
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 16: 邊界情況庫存查詢完整性")
    void shouldReturnCompleteInformationForBoundaryStockLevels(
            @ForAll("validProductIds") Long productId,
            @ForAll("validThresholds") Integer threshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建總庫存等於閾值的庫存實體
        Integer availableStock = threshold / 3;
        Integer temporaryReserved = threshold / 3;
        Integer confirmedReserved = threshold - availableStock - temporaryReserved;

        Inventory inventory = new Inventory(productId, availableStock, threshold);
        inventory.setId(System.currentTimeMillis());
        inventory.setTemporaryReserved(temporaryReserved);
        inventory.setConfirmedReserved(confirmedReserved);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory.setVersion(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));

        // 執行庫存查詢
        Optional<Inventory> result = inventoryService.findByProductId(productId);

        // 驗證查詢結果存在
        assertThat(result).isPresent();
        
        Inventory foundInventory = result.get();
        
        // 驗證邊界情況下的所有資訊都正確返回
        assertThat(foundInventory.getProductId()).isEqualTo(productId);
        assertThat(foundInventory.getAvailableStock()).isEqualTo(availableStock);
        assertThat(foundInventory.getTemporaryReserved()).isEqualTo(temporaryReserved);
        assertThat(foundInventory.getConfirmedReserved()).isEqualTo(confirmedReserved);
        assertThat(foundInventory.getLowStockThreshold()).isEqualTo(threshold);

        // 驗證 DTO 轉換的正確性
        InventoryDTO inventoryDTO = InventoryMapper.toDTO(foundInventory);
        Integer expectedTotalStock = availableStock + temporaryReserved + confirmedReserved;
        assertThat(inventoryDTO.getTotalStock()).isEqualTo(expectedTotalStock);
        assertThat(inventoryDTO.getIsLowStock()).isTrue(); // 等於閾值應該被標記為低庫存

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findByProductId(productId);
    }

    /**
     * 屬性測試：時間戳資訊的完整性
     */
    @Property(tries = 100)
    @Label("Feature: microservices-order-inventory, Property 16: 時間戳資訊完整性")
    void shouldReturnCompleteTimestampInformation(
            @ForAll("validProductIds") Long productId,
            @ForAll("validStockQuantities") Integer availableStock,
            @ForAll("validThresholds") Integer lowStockThreshold) {

        // 重置 mock 物件
        reset(inventoryRepository, reservationRepository, lockManager, restTemplate);

        // 創建帶有特定時間戳的庫存實體
        LocalDateTime specificTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
        
        Inventory inventory = new Inventory(productId, availableStock, lowStockThreshold);
        inventory.setId(System.currentTimeMillis());
        inventory.setTemporaryReserved(0);
        inventory.setConfirmedReserved(0);
        inventory.setUpdatedAt(specificTime);
        inventory.setVersion(1L);

        // 設定庫存存儲庫的回應
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(inventory));

        // 執行庫存查詢
        Optional<Inventory> result = inventoryService.findByProductId(productId);

        // 驗證查詢結果存在
        assertThat(result).isPresent();
        
        Inventory foundInventory = result.get();
        
        // 驗證時間戳資訊的完整性
        assertThat(foundInventory.getUpdatedAt()).isEqualTo(specificTime);

        // 驗證 DTO 轉換後時間戳資訊保持完整
        InventoryDTO inventoryDTO = InventoryMapper.toDTO(foundInventory);
        assertThat(inventoryDTO.getUpdatedAt()).isEqualTo(specificTime);

        // 驗證存儲庫方法被正確調用
        verify(inventoryRepository).findByProductId(productId);
    }

    // 資料生成器

    @Provide
    Arbitrary<Long> validProductIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Integer> validStockQuantities() {
        return Arbitraries.integers().between(0, 1000);
    }

    @Provide
    Arbitrary<Integer> validReservationQuantities() {
        return Arbitraries.integers().between(0, 500);
    }

    @Provide
    Arbitrary<Integer> validThresholds() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<Integer> highStockQuantities() {
        return Arbitraries.integers().between(1000, 10000);
    }

    @Provide
    Arbitrary<Integer> highReservationQuantities() {
        return Arbitraries.integers().between(100, 1000);
    }
}