package com.microservices.inventory.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 庫存資訊 DTO
 * 用於 API 回應的庫存資料傳輸物件
 */
@Schema(description = "庫存資訊")
public class InventoryDTO {
    
    @Schema(description = "庫存記錄 ID", example = "1")
    private Long id;
    
    @NotNull(message = "產品ID不能為空")
    @Schema(description = "產品 ID", example = "1001", required = true)
    private Long productId;
    
    @NotNull(message = "可用庫存不能為空")
    @Min(value = 0, message = "可用庫存不能為負數")
    @Schema(description = "可用庫存數量", example = "100", required = true)
    private Integer availableStock;
    
    @NotNull(message = "臨時預留數量不能為空")
    @Min(value = 0, message = "臨時預留數量不能為負數")
    @Schema(description = "臨時預留數量（購物車階段）", example = "5", required = true)
    private Integer temporaryReserved;
    
    @NotNull(message = "確認預留數量不能為空")
    @Min(value = 0, message = "確認預留數量不能為負數")
    @Schema(description = "確認預留數量（訂單階段）", example = "10", required = true)
    private Integer confirmedReserved;
    
    @NotNull(message = "低庫存閾值不能為空")
    @Min(value = 0, message = "低庫存閾值不能為負數")
    @Schema(description = "低庫存告警閾值", example = "20", required = true)
    private Integer lowStockThreshold;
    
    @Schema(description = "總庫存數量（可用+臨時預留+確認預留）", example = "115")
    private Integer totalStock;
    
    @Schema(description = "是否為低庫存", example = "false")
    private Boolean isLowStock;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最後更新時間", example = "2025-12-28 19:00:00")
    private LocalDateTime updatedAt;
    
    // 預設建構子
    public InventoryDTO() {}
    
    // 建構子
    public InventoryDTO(Long id, Long productId, Integer availableStock, Integer temporaryReserved,
                       Integer confirmedReserved, Integer lowStockThreshold, LocalDateTime updatedAt) {
        this.id = id;
        this.productId = productId;
        this.availableStock = availableStock;
        this.temporaryReserved = temporaryReserved;
        this.confirmedReserved = confirmedReserved;
        this.lowStockThreshold = lowStockThreshold;
        this.totalStock = availableStock + temporaryReserved + confirmedReserved;
        this.isLowStock = this.totalStock <= lowStockThreshold;
        this.updatedAt = updatedAt;
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
    
    public Integer getTotalStock() {
        return totalStock;
    }
    
    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }
    
    public Boolean getIsLowStock() {
        return isLowStock;
    }
    
    public void setIsLowStock(Boolean isLowStock) {
        this.isLowStock = isLowStock;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}