package com.microservices.inventory.repository;

import com.microservices.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * 庫存資料存取介面
 * 提供庫存相關的資料庫操作方法
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * 根據產品ID查找庫存
     */
    Optional<Inventory> findByProductId(Long productId);

    /**
     * 使用樂觀鎖根據產品ID查找庫存
     * 用於並發控制，防止庫存超賣
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdWithLock(@Param("productId") Long productId);

    /**
     * 查找所有低庫存的產品
     * 總庫存 <= 低庫存閾值
     */
    @Query("SELECT i FROM Inventory i WHERE (i.availableStock + i.temporaryReserved + i.confirmedReserved) <= i.lowStockThreshold")
    List<Inventory> findLowStockInventories();

    /**
     * 查找可用庫存大於指定數量的產品
     */
    @Query("SELECT i FROM Inventory i WHERE i.availableStock >= :quantity")
    List<Inventory> findByAvailableStockGreaterThanEqual(@Param("quantity") Integer quantity);

    /**
     * 檢查產品是否存在庫存記錄
     */
    boolean existsByProductId(Long productId);

    /**
     * 根據產品ID列表查找庫存
     */
    @Query("SELECT i FROM Inventory i WHERE i.productId IN :productIds")
    List<Inventory> findByProductIdIn(@Param("productIds") List<Long> productIds);

    /**
     * 統計總庫存數量
     */
    @Query("SELECT SUM(i.availableStock + i.temporaryReserved + i.confirmedReserved) FROM Inventory i")
    Long getTotalStockCount();

    /**
     * 統計可用庫存數量
     */
    @Query("SELECT SUM(i.availableStock) FROM Inventory i")
    Long getTotalAvailableStock();
}