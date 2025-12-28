package com.microservices.inventory.service;

import com.microservices.inventory.entity.InventoryReservation;
import com.microservices.inventory.exception.RetryExhaustedException;
import com.microservices.inventory.exception.SystemBusyException;
import com.microservices.inventory.service.impl.InventoryServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 可重試的庫存服務包裝器
 * 實現需求 10.3, 10.4: 樂觀鎖衝突時自動重試最多3次，達到上限後返回操作失敗
 */
@Service
public class RetryableInventoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryableInventoryService.class);
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    @Qualifier("inventoryRetryTemplate")
    private RetryTemplate retryTemplate;
    
    /**
     * 可重試的臨時預留操作
     */
    public InventoryReservation reserveTemporaryWithRetry(Long productId, Long userId, 
                                                         Integer quantity, LocalDateTime expiresAt) 
            throws InventoryService.InsufficientStockException, SystemBusyException {
        
        logger.info("開始可重試臨時預留: productId={}, userId={}, quantity={}", 
                   productId, userId, quantity);
        
        try {
            return retryTemplate.execute(new RetryCallback<InventoryReservation, Exception>() {
                @Override
                public InventoryReservation doWithRetry(RetryContext context) throws Exception {
                    if (context.getRetryCount() > 0) {
                        logger.info("重試臨時預留操作，第 {} 次重試: productId={}", 
                                   context.getRetryCount(), productId);
                    }
                    
                    try {
                        return inventoryService.reserveTemporary(productId, userId, quantity, expiresAt);
                    } catch (InventoryService.ConcurrentModificationException e) {
                        // 轉換為系統繁忙異常
                        throw new SystemBusyException("庫存預留", "請稍後重試", e);
                    }
                }
            });
        } catch (Exception e) {
            if (e instanceof InventoryService.InsufficientStockException) {
                throw (InventoryService.InsufficientStockException) e;
            }
            if (e instanceof SystemBusyException) {
                throw (SystemBusyException) e;
            }
            
            logger.error("臨時預留重試失敗: productId={}, userId={}", productId, userId, e);
            throw new RetryExhaustedException("臨時預留", 3, e);
        }
    }
    
    /**
     * 可重試的確認預留操作
     */
    public InventoryReservation confirmReservationWithRetry(Long productId, Long userId, Integer quantity) 
            throws InventoryService.ReservationNotFoundException, SystemBusyException {
        
        logger.info("開始可重試確認預留: productId={}, userId={}, quantity={}", 
                   productId, userId, quantity);
        
        try {
            return retryTemplate.execute(new RetryCallback<InventoryReservation, Exception>() {
                @Override
                public InventoryReservation doWithRetry(RetryContext context) throws Exception {
                    if (context.getRetryCount() > 0) {
                        logger.info("重試確認預留操作，第 {} 次重試: productId={}", 
                                   context.getRetryCount(), productId);
                    }
                    
                    try {
                        return inventoryService.confirmReservation(productId, userId, quantity);
                    } catch (InventoryService.ConcurrentModificationException e) {
                        // 轉換為系統繁忙異常
                        throw new SystemBusyException("預留確認", "請稍後重試", e);
                    }
                }
            });
        } catch (Exception e) {
            if (e instanceof InventoryService.ReservationNotFoundException) {
                throw (InventoryService.ReservationNotFoundException) e;
            }
            if (e instanceof SystemBusyException) {
                throw (SystemBusyException) e;
            }
            
            logger.error("確認預留重試失敗: productId={}, userId={}", productId, userId, e);
            throw new RetryExhaustedException("確認預留", 3, e);
        }
    }
    
    /**
     * 可重試的調整臨時預留操作
     */
    public InventoryReservation adjustTemporaryReservationWithRetry(Long productId, Long userId, 
                                                                   Integer newQuantity, LocalDateTime expiresAt) 
            throws InventoryService.InsufficientStockException, InventoryService.ReservationNotFoundException, SystemBusyException {
        
        logger.info("開始可重試調整臨時預留: productId={}, userId={}, newQuantity={}", 
                   productId, userId, newQuantity);
        
        try {
            return retryTemplate.execute(new RetryCallback<InventoryReservation, Exception>() {
                @Override
                public InventoryReservation doWithRetry(RetryContext context) throws Exception {
                    if (context.getRetryCount() > 0) {
                        logger.info("重試調整臨時預留操作，第 {} 次重試: productId={}", 
                                   context.getRetryCount(), productId);
                    }
                    
                    return inventoryService.adjustTemporaryReservation(productId, userId, newQuantity, expiresAt);
                }
            });
        } catch (Exception e) {
            if (e instanceof InventoryService.InsufficientStockException) {
                throw (InventoryService.InsufficientStockException) e;
            }
            if (e instanceof InventoryService.ReservationNotFoundException) {
                throw (InventoryService.ReservationNotFoundException) e;
            }
            
            logger.error("調整臨時預留重試失敗: productId={}, userId={}", productId, userId, e);
            throw new RetryExhaustedException("調整臨時預留", 3, e);
        }
    }
}