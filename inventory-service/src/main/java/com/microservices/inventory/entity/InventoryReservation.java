package com.microservices.inventory.entity;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 庫存預留記錄實體類別
 * 記錄庫存預留的詳細資訊，包括臨時預留和確認預留
 */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    @NotNull(message = "產品ID不能為空")
    private Long productId;
    
    @Column(nullable = false, length = 100)
    @NotBlank(message = "客戶ID不能為空")
    private String customerId;
    
    @Column(nullable = false)
    @NotNull(message = "預留數量不能為空")
    @Min(value = 1, message = "預留數量必須大於0")
    private Integer quantity;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "預留類型不能為空")
    private ReservationType type;
    
    @Column(nullable = false)
    @NotNull(message = "過期時間不能為空")
    private LocalDateTime expiresAt;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    // 預設建構子
    public InventoryReservation() {}
    
    // 建構子
    public InventoryReservation(Long productId, String customerId, Integer quantity, 
                               ReservationType type, LocalDateTime expiresAt) {
        this.productId = productId;
        this.customerId = customerId;
        this.quantity = quantity;
        this.type = type;
        this.expiresAt = expiresAt;
    }
    
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
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public ReservationType getType() {
        return type;
    }
    
    public void setType(ReservationType type) {
        this.type = type;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * 檢查預留是否已過期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * 檢查是否為臨時預留
     */
    public boolean isTemporary() {
        return type == ReservationType.TEMPORARY;
    }
    
    /**
     * 檢查是否為確認預留
     */
    public boolean isConfirmed() {
        return type == ReservationType.CONFIRMED;
    }
    
    @Override
    public String toString() {
        return "InventoryReservation{" +
                "id=" + id +
                ", productId=" + productId +
                ", customerId='" + customerId + '\'' +
                ", quantity=" + quantity +
                ", type=" + type +
                ", expiresAt=" + expiresAt +
                ", createdAt=" + createdAt +
                '}';
    }
}