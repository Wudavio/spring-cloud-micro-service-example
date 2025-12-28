package com.microservices.inventory.entity;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 庫存實體類別
 * 管理產品的庫存資訊，包括可用庫存、臨時預留和確認預留
 */
@Entity
@Table(name = "inventory")
public class Inventory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    @NotNull(message = "產品ID不能為空")
    private Long productId;
    
    @Column(nullable = false)
    @NotNull(message = "可用庫存不能為空")
    @Min(value = 0, message = "可用庫存不能為負數")
    private Integer availableStock;
    
    @Column(nullable = false)
    @NotNull(message = "臨時預留數量不能為空")
    @Min(value = 0, message = "臨時預留數量不能為負數")
    private Integer temporaryReserved;
    
    @Column(nullable = false)
    @NotNull(message = "確認預留數量不能為空")
    @Min(value = 0, message = "確認預留數量不能為負數")
    private Integer confirmedReserved;
    
    @Column(nullable = false)
    @NotNull(message = "低庫存閾值不能為空")
    @Min(value = 0, message = "低庫存閾值不能為負數")
    private Integer lowStockThreshold;
    
    @Version
    private Long version;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // 預設建構子
    public Inventory() {}
    
    // 建構子
    public Inventory(Long productId, Integer availableStock, Integer lowStockThreshold) {
        this.productId = productId;
        this.availableStock = availableStock;
        this.temporaryReserved = 0;
        this.confirmedReserved = 0;
        this.lowStockThreshold = lowStockThreshold;
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
    
    public Integer getAvailableStock() {
        return availableStock;
    }
    
    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }
    
    public Integer getTemporaryReserved() {
        return temporaryReserved;
    }
    
    public void setTemporaryReserved(Integer temporaryReserved) {
        this.temporaryReserved = temporaryReserved;
    }
    
    public Integer getConfirmedReserved() {
        return confirmedReserved;
    }
    
    public void setConfirmedReserved(Integer confirmedReserved) {
        this.confirmedReserved = confirmedReserved;
    }
    
    public Integer getLowStockThreshold() {
        return lowStockThreshold;
    }
    
    public void setLowStockThreshold(Integer lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * 計算總庫存（可用 + 臨時預留 + 確認預留）
     */
    public Integer getTotalStock() {
        return availableStock + temporaryReserved + confirmedReserved;
    }
    
    /**
     * 計算總可用庫存（僅可用庫存，不包括預留）
     * 用於低庫存告警檢查
     */
    public Integer getTotalAvailableStock() {
        return availableStock;
    }
    
    /**
     * 檢查是否為低庫存
     */
    public boolean isLowStock() {
        return getTotalStock() <= lowStockThreshold;
    }
    
    /**
     * 檢查是否有足夠的可用庫存進行預留
     */
    public boolean hasAvailableStock(Integer quantity) {
        return availableStock >= quantity;
    }
    
    @Override
    public String toString() {
        return "Inventory{" +
                "id=" + id +
                ", productId=" + productId +
                ", availableStock=" + availableStock +
                ", temporaryReserved=" + temporaryReserved +
                ", confirmedReserved=" + confirmedReserved +
                ", lowStockThreshold=" + lowStockThreshold +
                ", version=" + version +
                ", updatedAt=" + updatedAt +
                '}';
    }
}