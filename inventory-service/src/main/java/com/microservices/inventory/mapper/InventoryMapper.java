package com.microservices.inventory.mapper;

import com.microservices.inventory.dto.InventoryDTO;
import com.microservices.inventory.dto.ReservationDTO;
import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.entity.InventoryReservation;

/**
 * 庫存實體與 DTO 之間的映射工具類
 */
public class InventoryMapper {
    
    /**
     * 將庫存實體轉換為 DTO
     */
    public static InventoryDTO toDTO(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        
        return new InventoryDTO(
            inventory.getId(),
            inventory.getProductId(),
            inventory.getAvailableStock(),
            inventory.getTemporaryReserved(),
            inventory.getConfirmedReserved(),
            inventory.getLowStockThreshold(),
            inventory.getUpdatedAt()
        );
    }
    
    /**
     * 將預留記錄實體轉換為 DTO
     */
    public static ReservationDTO toDTO(InventoryReservation reservation) {
        if (reservation == null) {
            return null;
        }
        
        return new ReservationDTO(
            reservation.getId(),
            reservation.getProductId(),
            reservation.getUserId(),
            reservation.getQuantity(),
            reservation.getType(),
            reservation.getExpiresAt(),
            reservation.getCreatedAt()
        );
    }
}