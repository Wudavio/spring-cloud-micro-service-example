package com.microservices.inventory.service.impl;

import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.entity.InventoryReservation;
import com.microservices.inventory.entity.ReservationType;
import com.microservices.inventory.repository.InventoryRepository;
import com.microservices.inventory.repository.InventoryReservationRepository;
import com.microservices.inventory.service.InventoryService;
import com.microservices.inventory.service.InventoryLockManager;
import com.microservices.inventory.service.DistributedLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 庫存服務實現類
 * 實現庫存管理的核心業務邏輯
 */
@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceImpl.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryLockManager lockManager;
    private final RestTemplate restTemplate;

    @Autowired
    public InventoryServiceImpl(InventoryRepository inventoryRepository,
                               InventoryReservationRepository reservationRepository,
                               InventoryLockManager lockManager,
                               RestTemplate restTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.lockManager = lockManager;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Inventory> findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId);
    }

    @Override
    public Inventory createInventory(Long productId, Integer initialStock, Integer lowStockThreshold) {
        // 驗證產品是否存在於產品服務中
        if (!isProductExistsInProductService(productId)) {
            throw new IllegalArgumentException("產品不存在，無法創建庫存記錄。產品ID: " + productId);
        }

        // 檢查是否已存在該產品的庫存記錄
        if (inventoryRepository.existsByProductId(productId)) {
            throw new IllegalArgumentException("該產品已存在庫存記錄。產品ID: " + productId);
        }

        // 驗證參數
        if (initialStock < 0) {
            throw new IllegalArgumentException("初始庫存不能為負數");
        }
        if (lowStockThreshold < 0) {
            throw new IllegalArgumentException("低庫存閾值不能為負數");
        }

        Inventory inventory = new Inventory(productId, initialStock, lowStockThreshold);
        return inventoryRepository.save(inventory);
    }

    @Override
    public Inventory updateStock(Long productId, Integer newStock) {
        if (newStock < 0) {
            throw new IllegalArgumentException("庫存數量不能為負數");
        }

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("找不到產品庫存記錄。產品ID: " + productId));

        inventory.setAvailableStock(newStock);
        return inventoryRepository.save(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAvailableStock(Long productId, Integer quantity) {
        return inventoryRepository.findByProductId(productId)
                .map(inventory -> inventory.hasAvailableStock(quantity))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Inventory> findLowStockInventories() {
        return inventoryRepository.findLowStockInventories();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByProductId(Long productId) {
        return inventoryRepository.existsByProductId(productId);
    }

    @Override
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = MAX_RETRY_ATTEMPTS, backoff = @Backoff(delay = 1000, multiplier = 2))
    public InventoryReservation reserveTemporary(Long productId, String customerId, Integer quantity, LocalDateTime expiresAt)
            throws InsufficientStockException, ConcurrentModificationException {
        
        logger.info("開始臨時預留庫存: productId={}, customerId={}, quantity={}", productId, customerId, quantity);
        
        try {
            return lockManager.executeWithProductLock(productId, () -> {
                try {
                    return performTemporaryReservation(productId, customerId, quantity, expiresAt);
                } catch (InsufficientStockException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (DistributedLockService.LockAcquisitionException e) {
            logger.error("無法獲取分散式鎖進行臨時預留: productId={}", productId, e);
            throw new ConcurrentModificationException("系統繁忙，請稍後重試");
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InsufficientStockException) {
                throw (InsufficientStockException) e.getCause();
            }
            throw e;
        }
    }

    private InventoryReservation performTemporaryReservation(Long productId, String customerId, Integer quantity, LocalDateTime expiresAt)
            throws InsufficientStockException {
        
        // 使用樂觀鎖查詢庫存
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("找不到產品庫存記錄。產品ID: " + productId));

        // 檢查庫存是否足夠
        if (!inventory.hasAvailableStock(quantity)) {
            throw new InsufficientStockException(
                String.format("庫存不足。產品ID: %d, 需要數量: %d, 可用庫存: %d", 
                    productId, quantity, inventory.getAvailableStock()));
        }

        // 檢查是否已存在該客戶的臨時預留
        List<InventoryReservation> existingReservations = reservationRepository
                .findByProductIdAndCustomerIdAndType(productId, customerId, ReservationType.TEMPORARY);
        
        if (!existingReservations.isEmpty()) {
            // 如果已存在，先釋放舊的預留
            for (InventoryReservation existing : existingReservations) {
                inventory.setAvailableStock(inventory.getAvailableStock() + existing.getQuantity());
                inventory.setTemporaryReserved(inventory.getTemporaryReserved() - existing.getQuantity());
                reservationRepository.delete(existing);
            }
        }

        // 執行預留操作
        inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
        inventory.setTemporaryReserved(inventory.getTemporaryReserved() + quantity);
        inventoryRepository.save(inventory);

        // 創建預留記錄
        InventoryReservation reservation = new InventoryReservation(productId, customerId, quantity, ReservationType.TEMPORARY, expiresAt);
        reservation = reservationRepository.save(reservation);

        logger.info("臨時預留成功: productId={}, customerId={}, quantity={}, reservationId={}", 
                   productId, customerId, quantity, reservation.getId());
        
        return reservation;
    }

    @Override
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = MAX_RETRY_ATTEMPTS, backoff = @Backoff(delay = 1000, multiplier = 2))
    public InventoryReservation confirmReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException, ConcurrentModificationException {
        
        logger.info("開始確認預留: productId={}, customerId={}, quantity={}", productId, customerId, quantity);
        
        try {
            return lockManager.executeWithProductLock(productId, () -> {
                try {
                    return performConfirmReservation(productId, customerId, quantity);
                } catch (ReservationNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (DistributedLockService.LockAcquisitionException e) {
            logger.error("無法獲取分散式鎖進行確認預留: productId={}", productId, e);
            throw new ConcurrentModificationException("系統繁忙，請稍後重試");
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ReservationNotFoundException) {
                throw (ReservationNotFoundException) e.getCause();
            }
            throw e;
        }
    }

    private InventoryReservation performConfirmReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException {
        
        // 使用樂觀鎖查詢庫存
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("找不到產品庫存記錄。產品ID: " + productId));

        // 查找臨時預留記錄
        List<InventoryReservation> temporaryReservations = reservationRepository
                .findByProductIdAndCustomerIdAndType(productId, customerId, ReservationType.TEMPORARY);
        
        if (temporaryReservations.isEmpty()) {
            throw new ReservationNotFoundException(
                String.format("找不到臨時預留記錄。產品ID: %d, 客戶ID: %s", productId, customerId));
        }

        // 計算總的臨時預留數量
        int totalTemporaryQuantity = temporaryReservations.stream()
                .mapToInt(InventoryReservation::getQuantity)
                .sum();

        if (totalTemporaryQuantity < quantity) {
            throw new ReservationNotFoundException(
                String.format("臨時預留數量不足。產品ID: %d, 客戶ID: %s, 需要: %d, 可用: %d", 
                    productId, customerId, quantity, totalTemporaryQuantity));
        }

        // 刪除臨時預留記錄
        for (InventoryReservation temp : temporaryReservations) {
            reservationRepository.delete(temp);
        }

        // 更新庫存數量
        inventory.setTemporaryReserved(inventory.getTemporaryReserved() - totalTemporaryQuantity);
        inventory.setConfirmedReserved(inventory.getConfirmedReserved() + quantity);
        
        // 如果確認數量小於臨時預留數量，將差額釋放回可用庫存
        if (quantity < totalTemporaryQuantity) {
            int releaseQuantity = totalTemporaryQuantity - quantity;
            inventory.setAvailableStock(inventory.getAvailableStock() + releaseQuantity);
        }
        
        inventoryRepository.save(inventory);

        // 創建確認預留記錄（永不過期，直到訂單完成或取消）
        LocalDateTime neverExpires = LocalDateTime.now().plusYears(10);
        InventoryReservation confirmedReservation = new InventoryReservation(
                productId, customerId, quantity, ReservationType.CONFIRMED, neverExpires);
        confirmedReservation = reservationRepository.save(confirmedReservation);

        logger.info("確認預留成功: productId={}, customerId={}, quantity={}, reservationId={}", 
                   productId, customerId, quantity, confirmedReservation.getId());
        
        return confirmedReservation;
    }

    @Override
    public void releaseTemporaryReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException {
        
        logger.info("開始釋放臨時預留: productId={}, customerId={}, quantity={}", productId, customerId, quantity);
        
        try {
            lockManager.executeWithProductLock(productId, () -> {
                try {
                    performReleaseTemporaryReservation(productId, customerId, quantity);
                } catch (ReservationNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (DistributedLockService.LockAcquisitionException e) {
            logger.error("無法獲取分散式鎖進行臨時預留釋放: productId={}", productId, e);
            throw new RuntimeException("系統繁忙，請稍後重試", e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ReservationNotFoundException) {
                throw (ReservationNotFoundException) e.getCause();
            }
            throw e;
        }
    }

    private void performReleaseTemporaryReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException {
        
        // 查詢庫存
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("找不到產品庫存記錄。產品ID: " + productId));

        // 查找臨時預留記錄
        List<InventoryReservation> temporaryReservations = reservationRepository
                .findByProductIdAndCustomerIdAndType(productId, customerId, ReservationType.TEMPORARY);
        
        if (temporaryReservations.isEmpty()) {
            throw new ReservationNotFoundException(
                String.format("找不到臨時預留記錄。產品ID: %d, 客戶ID: %s", productId, customerId));
        }

        // 計算總的臨時預留數量
        int totalTemporaryQuantity = temporaryReservations.stream()
                .mapToInt(InventoryReservation::getQuantity)
                .sum();

        int releaseQuantity = Math.min(quantity, totalTemporaryQuantity);

        // 刪除預留記錄
        for (InventoryReservation temp : temporaryReservations) {
            reservationRepository.delete(temp);
        }

        // 更新庫存數量
        inventory.setAvailableStock(inventory.getAvailableStock() + releaseQuantity);
        inventory.setTemporaryReserved(inventory.getTemporaryReserved() - releaseQuantity);
        inventoryRepository.save(inventory);

        // 如果還有剩餘需要預留的數量，重新創建預留記錄
        if (totalTemporaryQuantity > releaseQuantity) {
            int remainingQuantity = totalTemporaryQuantity - releaseQuantity;
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(24); // 預設24小時過期
            InventoryReservation newReservation = new InventoryReservation(
                    productId, customerId, remainingQuantity, ReservationType.TEMPORARY, expiresAt);
            reservationRepository.save(newReservation);
            
            inventory.setAvailableStock(inventory.getAvailableStock() - remainingQuantity);
            inventory.setTemporaryReserved(inventory.getTemporaryReserved() + remainingQuantity);
            inventoryRepository.save(inventory);
        }

        logger.info("臨時預留釋放成功: productId={}, customerId={}, releaseQuantity={}", 
                   productId, customerId, releaseQuantity);
    }

    @Override
    public void releaseConfirmedReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException {
        
        logger.info("開始釋放確認預留: productId={}, customerId={}, quantity={}", productId, customerId, quantity);
        
        try {
            lockManager.executeWithProductLock(productId, () -> {
                try {
                    performReleaseConfirmedReservation(productId, customerId, quantity);
                    return null; // 返回 null 因為這是 void 方法
                } catch (ReservationNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (DistributedLockService.LockAcquisitionException e) {
            logger.error("無法獲取分散式鎖進行確認預留釋放: productId={}", productId, e);
            throw new RuntimeException("系統繁忙，請稍後重試", e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ReservationNotFoundException) {
                throw (ReservationNotFoundException) e.getCause();
            }
            throw e;
        }
    }

    private void performReleaseConfirmedReservation(Long productId, String customerId, Integer quantity)
            throws ReservationNotFoundException {
        
        // 查詢庫存
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("找不到產品庫存記錄。產品ID: " + productId));

        // 查找確認預留記錄
        List<InventoryReservation> confirmedReservations = reservationRepository
                .findByProductIdAndCustomerIdAndType(productId, customerId, ReservationType.CONFIRMED);
        
        if (confirmedReservations.isEmpty()) {
            throw new ReservationNotFoundException(
                String.format("找不到確認預留記錄。產品ID: %d, 客戶ID: %s", productId, customerId));
        }

        // 計算總的確認預留數量
        int totalConfirmedQuantity = confirmedReservations.stream()
                .mapToInt(InventoryReservation::getQuantity)
                .sum();

        int releaseQuantity = Math.min(quantity, totalConfirmedQuantity);

        // 刪除預留記錄
        for (InventoryReservation confirmed : confirmedReservations) {
            reservationRepository.delete(confirmed);
        }

        // 更新庫存數量
        inventory.setAvailableStock(inventory.getAvailableStock() + releaseQuantity);
        inventory.setConfirmedReserved(inventory.getConfirmedReserved() - releaseQuantity);
        inventoryRepository.save(inventory);

        // 如果還有剩餘的確認預留數量，重新創建預留記錄
        if (totalConfirmedQuantity > releaseQuantity) {
            int remainingQuantity = totalConfirmedQuantity - releaseQuantity;
            LocalDateTime neverExpires = LocalDateTime.now().plusYears(10);
            InventoryReservation newReservation = new InventoryReservation(
                    productId, customerId, remainingQuantity, ReservationType.CONFIRMED, neverExpires);
            reservationRepository.save(newReservation);
            
            inventory.setConfirmedReserved(inventory.getConfirmedReserved() + remainingQuantity);
            inventoryRepository.save(inventory);
        }

        logger.info("確認預留釋放成功: productId={}, customerId={}, releaseQuantity={}", 
                   productId, customerId, releaseQuantity);
    }

    @Override
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = MAX_RETRY_ATTEMPTS, backoff = @Backoff(delay = 1000, multiplier = 2))
    public InventoryReservation adjustTemporaryReservation(Long productId, String customerId, Integer newQuantity, LocalDateTime expiresAt)
            throws InsufficientStockException, ReservationNotFoundException {
        
        logger.info("開始調整臨時預留: productId={}, customerId={}, newQuantity={}", productId, customerId, newQuantity);
        
        try {
            return lockManager.executeWithProductLock(productId, () -> {
                try {
                    return performAdjustTemporaryReservation(productId, customerId, newQuantity, expiresAt);
                } catch (InsufficientStockException | ReservationNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (DistributedLockService.LockAcquisitionException e) {
            logger.error("無法獲取分散式鎖進行臨時預留調整: productId={}", productId, e);
            throw new RuntimeException("系統繁忙，請稍後重試", e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InsufficientStockException) {
                throw (InsufficientStockException) e.getCause();
            }
            if (e.getCause() instanceof ReservationNotFoundException) {
                throw (ReservationNotFoundException) e.getCause();
            }
            throw e;
        }
    }

    private InventoryReservation performAdjustTemporaryReservation(Long productId, String customerId, Integer newQuantity, LocalDateTime expiresAt)
            throws InsufficientStockException, ReservationNotFoundException {
        
        // 使用樂觀鎖查詢庫存
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("找不到產品庫存記錄。產品ID: " + productId));

        // 查找現有的臨時預留記錄
        List<InventoryReservation> existingReservations = reservationRepository
                .findByProductIdAndCustomerIdAndType(productId, customerId, ReservationType.TEMPORARY);
        
        if (existingReservations.isEmpty()) {
            throw new ReservationNotFoundException(
                String.format("找不到臨時預留記錄。產品ID: %d, 客戶ID: %s", productId, customerId));
        }

        // 計算現有預留數量
        int currentQuantity = existingReservations.stream()
                .mapToInt(InventoryReservation::getQuantity)
                .sum();

        // 計算需要調整的數量
        int adjustmentQuantity = newQuantity - currentQuantity;

        if (adjustmentQuantity > 0) {
            // 需要增加預留，檢查庫存是否足夠
            if (!inventory.hasAvailableStock(adjustmentQuantity)) {
                throw new InsufficientStockException(
                    String.format("庫存不足以增加預留。產品ID: %d, 需要增加: %d, 可用庫存: %d", 
                        productId, adjustmentQuantity, inventory.getAvailableStock()));
            }
        }

        // 刪除現有預留記錄
        for (InventoryReservation existing : existingReservations) {
            reservationRepository.delete(existing);
        }

        // 更新庫存數量
        inventory.setAvailableStock(inventory.getAvailableStock() + currentQuantity - newQuantity);
        inventory.setTemporaryReserved(inventory.getTemporaryReserved() - currentQuantity + newQuantity);
        inventoryRepository.save(inventory);

        // 創建新的預留記錄
        InventoryReservation newReservation = new InventoryReservation(
                productId, customerId, newQuantity, ReservationType.TEMPORARY, expiresAt);
        newReservation = reservationRepository.save(newReservation);

        logger.info("臨時預留調整成功: productId={}, customerId={}, oldQuantity={}, newQuantity={}, reservationId={}", 
                   productId, customerId, currentQuantity, newQuantity, newReservation.getId());
        
        return newReservation;
    }

    @Override
    @Transactional
    public int cleanupExpiredTemporaryReservations() {
        logger.info("開始清理過期的臨時預留");
        
        LocalDateTime now = LocalDateTime.now();
        List<InventoryReservation> expiredReservations = reservationRepository
                .findByTypeAndExpiresAtBefore(ReservationType.TEMPORARY, now);
        
        int cleanedCount = 0;
        for (InventoryReservation expired : expiredReservations) {
            try {
                lockManager.executeWithProductLock(expired.getProductId(), () -> {
                    // 查詢庫存並釋放預留
                    Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(expired.getProductId());
                    if (inventoryOpt.isPresent()) {
                        Inventory inventory = inventoryOpt.get();
                        inventory.setAvailableStock(inventory.getAvailableStock() + expired.getQuantity());
                        inventory.setTemporaryReserved(inventory.getTemporaryReserved() - expired.getQuantity());
                        inventoryRepository.save(inventory);
                    }
                    
                    // 刪除過期預留記錄
                    reservationRepository.delete(expired);
                });
                cleanedCount++;
            } catch (DistributedLockService.LockAcquisitionException e) {
                logger.warn("無法獲取鎖清理過期預留: productId={}, reservationId={}", 
                           expired.getProductId(), expired.getId(), e);
            }
        }
        
        logger.info("清理過期臨時預留完成，清理數量: {}", cleanedCount);
        return cleanedCount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryReservation> findReservationsByCustomer(String customerId) {
        return reservationRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryReservation> findReservationsByProduct(Long productId) {
        return reservationRepository.findByProductId(productId);
    }

    /**
     * 驗證產品是否存在於產品服務中
     * 這裡使用 RestTemplate 調用產品服務的 API
     */
    private boolean isProductExistsInProductService(Long productId) {
        try {
            // 在實際環境中，這裡會調用產品服務的 API
            // 例如: GET /api/products/{productId}/exists
            // 為了測試目的，這裡簡化處理
            String url = "http://product-service/api/products/" + productId + "/exists";
            Boolean exists = restTemplate.getForObject(url, Boolean.class);
            return exists != null && exists;
        } catch (Exception e) {
            // 如果產品服務不可用，為了安全起見，返回 false
            return false;
        }
    }
}