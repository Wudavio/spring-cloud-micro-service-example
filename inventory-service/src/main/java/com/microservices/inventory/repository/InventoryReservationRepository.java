package com.microservices.inventory.repository;

import com.microservices.inventory.entity.InventoryReservation;
import com.microservices.inventory.entity.ReservationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 庫存預留記錄資料存取介面
 * 提供預留記錄相關的資料庫操作方法
 */
@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    /**
     * 根據產品ID和用戶ID查找預留記錄
     */
    List<InventoryReservation> findByProductIdAndUserId(Long productId, Long userId);

    /**
     * 根據產品ID、用戶ID和預留類型查找預留記錄
     */
    List<InventoryReservation> findByProductIdAndUserIdAndType(Long productId, Long userId, ReservationType type);

    /**
     * 根據用戶ID查找所有預留記錄
     */
    List<InventoryReservation> findByUserId(Long userId);

    /**
     * 根據產品ID查找所有預留記錄
     */
    List<InventoryReservation> findByProductId(Long productId);

    /**
     * 查找已過期的臨時預留記錄
     */
    @Query("SELECT r FROM InventoryReservation r WHERE r.type = :type AND r.expiresAt < :currentTime")
    List<InventoryReservation> findExpiredReservations(@Param("type") ReservationType type, @Param("currentTime") LocalDateTime currentTime);

    /**
     * 查找指定時間之前過期的臨時預留記錄
     */
    List<InventoryReservation> findByTypeAndExpiresAtBefore(ReservationType type, LocalDateTime expiresAt);

    /**
     * 根據產品ID和預留類型統計預留數量
     */
    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM InventoryReservation r WHERE r.productId = :productId AND r.type = :type")
    Integer sumQuantityByProductIdAndType(@Param("productId") Long productId, @Param("type") ReservationType type);

    /**
     * 根據客戶ID和預留類型統計預留數量
     */
    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM InventoryReservation r WHERE r.userId = :userId AND r.type = :type")
    Integer sumQuantityByUserIdAndType(@Param("userId") Long userId, @Param("type") ReservationType type);

    /**
     * 刪除指定產品和客戶的特定類型預留記錄
     */
    @Modifying
    @Query("DELETE FROM InventoryReservation r WHERE r.productId = :productId AND r.userId = :userId AND r.type = :type")
    void deleteByProductIdAndUserIdAndType(@Param("productId") Long productId, @Param("userId") Long userId, @Param("type") ReservationType type);

    /**
     * 刪除過期的預留記錄
     */
    @Modifying
    @Query("DELETE FROM InventoryReservation r WHERE r.type = :type AND r.expiresAt < :currentTime")
    int deleteExpiredReservations(@Param("type") ReservationType type, @Param("currentTime") LocalDateTime currentTime);

    /**
     * 檢查是否存在指定的預留記錄
     */
    boolean existsByProductIdAndUserIdAndType(Long productId, Long userId, ReservationType type);

    /**
     * 查找特定產品的所有有效預留記錄（未過期）
     */
    @Query("SELECT r FROM InventoryReservation r WHERE r.productId = :productId AND r.expiresAt > :currentTime")
    List<InventoryReservation> findValidReservationsByProductId(@Param("productId") Long productId, @Param("currentTime") LocalDateTime currentTime);
}