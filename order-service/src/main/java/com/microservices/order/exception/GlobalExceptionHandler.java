package com.microservices.order.exception;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全域異常處理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 處理訂單未找到異常
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFoundException(OrderNotFoundException ex) {
        logger.error("訂單未找到: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "ORDER_NOT_FOUND",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * 處理購物車未找到異常
     */
    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCartNotFoundException(CartNotFoundException ex) {
        logger.error("購物車未找到: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "CART_NOT_FOUND",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * 處理產品未找到異常
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFoundException(ProductNotFoundException ex) {
        logger.error("產品未找到: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "PRODUCT_NOT_FOUND",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * 處理庫存不足異常
     */
    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientInventoryException(InsufficientInventoryException ex) {
        logger.error("庫存不足: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            "INSUFFICIENT_INVENTORY",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 處理參數驗證異常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        logger.error("參數驗證失敗: {}", errors);
        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            "參數驗證失敗",
            LocalDateTime.now(),
            errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 處理 Feign 異常
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(FeignException ex) {
        logger.error("服務調用失敗: {}", ex.getMessage());
        
        String message = "服務調用失敗";
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (ex.status() == 404) {
            message = "請求的資源不存在";
            status = HttpStatus.NOT_FOUND;
        } else if (ex.status() == 400) {
            message = "請求參數錯誤";
            status = HttpStatus.BAD_REQUEST;
        }
        
        ErrorResponse error = new ErrorResponse(
            "SERVICE_CALL_ERROR",
            message,
            LocalDateTime.now()
        );
        return ResponseEntity.status(status).body(error);
    }
    
    /**
     * 處理一般異常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, 
                                                               HttpServletRequest request) {
        // 不攔截 Swagger/OpenAPI 相關的請求
        String requestURI = request.getRequestURI();
        if (requestURI.contains("/v3/api-docs") || 
            requestURI.contains("/swagger-ui") || 
            requestURI.contains("/swagger-resources")) {
            throw new RuntimeException(ex);
        }
        
        logger.error("系統異常: ", ex);
        ErrorResponse error = new ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "系統內部錯誤",
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * 錯誤回應類
     */
    public static class ErrorResponse {
        private String code;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, String> details;
        
        public ErrorResponse(String code, String message, LocalDateTime timestamp) {
            this.code = code;
            this.message = message;
            this.timestamp = timestamp;
        }
        
        public ErrorResponse(String code, String message, LocalDateTime timestamp, Map<String, String> details) {
            this.code = code;
            this.message = message;
            this.timestamp = timestamp;
            this.details = details;
        }
        
        // Getters and Setters
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
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