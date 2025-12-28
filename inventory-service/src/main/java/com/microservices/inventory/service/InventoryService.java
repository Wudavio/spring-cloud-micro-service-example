package com.microservices.inventory.service;

import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.entity.InventoryReservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 庫存服務介面
 * 定義庫存管理的核心業務邏輯方法
 */
public interface InventoryService {

    /**
     * 根據產品ID查找庫存
     */
    Optional<Inventory> findByProductId(Long productId);

    /**
     * 創建新的庫存記錄
     */
    Inventory createInventory(Long productId, Integer initialStock, Integer lowStockThreshold);

    /**
     * 更新庫存數量
     */
    Inventory updateStock(Long productId, Integer newStock);

    /**
     * 檢查產品是否有足夠的可用庫存
     */
    boolean hasAvailableStock(Long productId, Integer quantity);

    /**
     * 查找所有低庫存的產品
     */
    List<Inventory> findLowStockInventories();

    /**
     * 檢查產品是否存在庫存記錄
     */
    boolean existsByProductId(Long productId);

    // 新增的預留和釋放方法

    /**
     * 臨時預留庫存（購物車階段）
     * 使用分散式鎖和樂觀鎖確保並發安全
     * 
     * @param productId 產品ID
     * @param customerId 客戶ID
     * @param quantity 預留數量
     * @param expiresAt 過期時間
     * @return 預留記錄
     * @throws InsufficientStockException 庫存不足時拋出
     * @throws ConcurrentModificationException 並發衝突時拋出
     */
    InventoryReservation reserveTemporary(Long productId, String customerId, Integer quantity, LocalDateTime expiresAt)
            throws InsufficientStockException, ConcurrentModificationException;

    /**
     * 將臨時預留轉換為正式預留（下單階段）
     * 使用分散式鎖確保原子性
     * 
     * @param productId 產品ID
     * @param customerId 客戶ID
     * @param quantity 確認數量
     * @return 確認預留記錄
     * @throws ReservationNotFoundException 找不到臨時預留時拋出
     * @throws ConcurrentModificationException 並發衝突時拋出
     */
    InventoryReservation confirmReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException, ConcurrentModificationException;

    /**
     * 釋放臨時預留（購物車移除商品）
     * 
     * @param productId 產品ID
     * @param customerId 客戶ID
     * @param quantity 釋放數量
     * @throws ReservationNotFoundException 找不到預留記錄時拋出
     */
    void releaseTemporaryReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException;

    /**
     * 釋放正式預留（訂單取消）
     * 
     * @param productId 產品ID
     * @param customerId 客戶ID
     * @param quantity 釋放數量
     * @throws ReservationNotFoundException 找不到預留記錄時拋出
     */
    void releaseConfirmedReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException;

    /**
     * 調整臨時預留數量（購物車數量變更）
     * 
     * @param productId 產品ID
     * @param customerId 客戶ID
     * @param newQuantity 新的預留數量
     * @param expiresAt 新的過期時間
     * @return 更新後的預留記錄
     * @throws InsufficientStockException 庫存不足時拋出
     * @throws ReservationNotFoundException 找不到預留記錄時拋出
     */
    InventoryReservation adjustTemporaryReservation(Long productId, String customerId, Integer newQuantity, LocalDateTime expiresAt)
            throws InsufficientStockException, ReservationNotFoundException;

    /**
     * 清理過期的臨時預留
     * 
     * @return 清理的記錄數量
     */
    int cleanupExpiredTemporaryReservations();

    /**
     * 查詢客戶的所有預留記錄
     * 
     * @param customerId 客戶ID
     * @return 預留記錄列表
     */
    List<InventoryReservation> findReservationsByCustomer(String customerId);

    /**
     * 查詢產品的所有預留記錄
     * 
     * @param productId 產品ID
     * @return 預留記錄列表
     */
    List<InventoryReservation> findReservationsByProduct(Long productId);

    // 異常類定義
    class InsufficientStockException extends Exception {
        public InsufficientStockException(String message) {
            super(message);
        }
    }

    class ReservationNotFoundException extends Exception {
        public ReservationNotFoundException(String message) {
            super(message);
        }
    }

    class ConcurrentModificationException extends Exception {
        public ConcurrentModificationException(String message) {
            super(message);
        }
    }
}