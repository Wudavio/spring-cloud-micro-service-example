package com.microservices.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 庫存服務客戶端
 */
@FeignClient(name = "inventory-service", path = "/api/inventory")
public interface InventoryServiceClient {
    
    /**
     * 預留庫存
     */
    @PostMapping("/{productId}/reserve")
    ReservationDTO reserveInventory(@PathVariable("productId") Long productId, 
                                   @RequestBody ReserveInventoryRequest request);
    
    /**
     * 釋放庫存
     */
    @PostMapping("/{productId}/release")
    void releaseInventory(@PathVariable("productId") Long productId, 
                         @RequestBody ReleaseInventoryRequest request);
    
    /**
     * 確認預留
     */
    @PostMapping("/{productId}/confirm")
    void confirmReservation(@PathVariable("productId") Long productId, 
                           @RequestBody ConfirmReservationRequest request);
    
    /**
     * 預留庫存請求
     */
    class ReserveInventoryRequest {
        private Long userId;
        private Integer quantity;
        private String type; // TEMPORARY 或 CONFIRMED
        
        public ReserveInventoryRequest() {}
        
        public ReserveInventoryRequest(Long userId, Integer quantity, String type) {
            this.userId = userId;
            this.quantity = quantity;
            this.type = type;
        }
        
        // Getters and Setters
        public Long getUserId() {
            return userId;
        }
        
        public void setUserId(Long userId) {
            this.userId = userId;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
    }
    
    /**
     * 釋放庫存請求
     */
    class ReleaseInventoryRequest {
        private Long userId;
        private Integer quantity;
        private String type;
        
        public ReleaseInventoryRequest() {}
        
        public ReleaseInventoryRequest(Long userId, Integer quantity, String type) {
            this.userId = userId;
            this.quantity = quantity;
            this.type = type;
        }
        
        // Getters and Setters
        public Long getUserId() {
            return userId;
        }
        
        public void setUserId(Long userId) {
            this.userId = userId;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
    }
    
    /**
     * 確認預留請求
     */
    class ConfirmReservationRequest {
        private Long userId;
        private Integer quantity;
        
        public ConfirmReservationRequest() {}
        
        public ConfirmReservationRequest(Long userId, Integer quantity) {
            this.userId = userId;
            this.quantity = quantity;
        }
        
        // Getters and Setters
        public Long getUserId() {
            return userId;
        }
        
        public void setUserId(Long userId) {
            this.userId = userId;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
    
    /**
     * 預留回應
     */
    class ReservationDTO {
        private Long id;
        private Long productId;
        private String customerId;
        private Integer quantity;
        private String type;
        private java.time.LocalDateTime expiresAt;
        
        public ReservationDTO() {}
        
        // Getters and Setters
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public Long getProductId() {
            return productId;
        }
        
        public void setProductId(Long productId) {
            this.productId = productId;
        }
        
        public String getUserId() {
            return customerId;
        }
        
        public void setUserId(String customerId) {
            this.customerId = customerId;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public java.time.LocalDateTime getExpiresAt() {
            return expiresAt;
        }
        
        public void setExpiresAt(java.time.LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}