package com.microservices.inventory.exception;

import com.microservices.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全域異常處理器
 * 統一處理庫存服務的異常回應
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 處理庫存不足異常
     */
    @ExceptionHandler(InventoryService.InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStockException(
            InventoryService.InsufficientStockException ex) {
        logger.warn("庫存不足: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "INSUFFICIENT_STOCK",
            ex.getMessage(),
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * 處理預留記錄未找到異常
     */
    @ExceptionHandler(InventoryService.ReservationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReservationNotFoundException(
            InventoryService.ReservationNotFoundException ex) {
        logger.warn("預留記錄未找到: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "RESERVATION_NOT_FOUND",
            ex.getMessage(),
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * 處理並發修改異常
     */
    @ExceptionHandler(InventoryService.ConcurrentModificationException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentModificationException(
            InventoryService.ConcurrentModificationException ex) {
        logger.warn("並發修改衝突: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            "CONCURRENT_MODIFICATION",
            "系統繁忙，請稍後重試",
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
    
    /**
     * 處理參數驗證異常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        logger.warn("參數驗證失敗: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse errorResponse = new ErrorResponse(
            "VALIDATION_ERROR",
            "請求參數驗證失敗",
            LocalDateTime.now(),
            errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * 處理一般異常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        logger.error("系統異常: ", ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "系統內部錯誤，請聯繫管理員",
            LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * 錯誤回應 DTO
     */
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, String> details;
        
        public ErrorResponse(String errorCode, String message, LocalDateTime timestamp) {
            this.errorCode = errorCode;
            this.message = message;
            this.timestamp = timestamp;
        }
        
        public ErrorResponse(String errorCode, String message, LocalDateTime timestamp, Map<String, String> details) {
            this.errorCode = errorCode;
            this.message = message;
            this.timestamp = timestamp;
            this.details = details;
        }
        
        // Getters and Setters
        public String getErrorCode() {
            return errorCode;
        }
        
        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
        
        public Map<String, String> getDetails() {
            return details;
        }
        
        public void setDetails(Map<String, String> details) {
            this.details = details;
        }
    }
}