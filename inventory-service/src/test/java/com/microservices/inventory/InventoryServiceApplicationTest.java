package com.microservices.inventory;

import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.entity.InventoryReservation;
import com.microservices.inventory.entity.ReservationType;
import com.microservices.inventory.repository.InventoryRepository;
import com.microservices.inventory.repository.InventoryReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 庫存服務應用程式整合測試
 * 驗證核心結構和基本功能是否正常工作
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryServiceApplicationTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Test
    void contextLoads() {
        // 驗證 Spring 上下文是否正確載入
        assertThat(inventoryRepository).isNotNull();
        assertThat(reservationRepository).isNotNull();
    }

    @Test
    void testInventoryEntityBasicOperations() {
        // 測試庫存實體的基本 CRUD 操作
        Inventory inventory = new Inventory(1L, 100, 10);
        
        // 保存庫存
        Inventory saved = inventoryRepository.save(inventory);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProductId()).isEqualTo(1L);
        assertThat(saved.getAvailableStock()).isEqualTo(100);
        assertThat(saved.getLowStockThreshold()).isEqualTo(10);
        
        // 查詢庫存
        var found = inventoryRepository.findByProductId(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getAvailableStock()).isEqualTo(100);
        
        // 測試業務方法
        assertThat(found.get().hasAvailableStock(50)).isTrue();
        assertThat(found.get().hasAvailableStock(150)).isFalse();
        assertThat(found.get().getTotalStock()).isEqualTo(100);
        assertThat(found.get().isLowStock()).isFalse();
    }

    @Test
    void testInventoryReservationEntityBasicOperations() {
        // 測試預留記錄實體的基本 CRUD 操作
        InventoryReservation reservation = new InventoryReservation(
            1L, 
            "customer123", 
            5, 
            ReservationType.TEMPORARY, 
            LocalDateTime.now().plusHours(1)
        );
        
        // 保存預留記錄
        InventoryReservation saved = reservationRepository.save(reservation);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProductId()).isEqualTo(1L);
        assertThat(saved.getCustomerId()).isEqualTo("customer123");
        assertThat(saved.getQuantity()).isEqualTo(5);
        assertThat(saved.getType()).isEqualTo(ReservationType.TEMPORARY);
        
        // 查詢預留記錄
        var found = reservationRepository.findByProductIdAndCustomerId(1L, "customer123");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getQuantity()).isEqualTo(5);
        
        // 測試業務方法
        assertThat(found.get(0).isTemporary()).isTrue();
        assertThat(found.get(0).isConfirmed()).isFalse();
        assertThat(found.get(0).isExpired()).isFalse();
    }

    @Test
    void testReservationTypeEnum() {
        // 測試預留類型枚舉
        assertThat(ReservationType.TEMPORARY.getDescription()).isEqualTo("臨時預留");
        assertThat(ReservationType.CONFIRMED.getDescription()).isEqualTo("確認預留");
        assertThat(ReservationType.TEMPORARY.toString()).isEqualTo("臨時預留");
        assertThat(ReservationType.CONFIRMED.toString()).isEqualTo("確認預留");
    }
}