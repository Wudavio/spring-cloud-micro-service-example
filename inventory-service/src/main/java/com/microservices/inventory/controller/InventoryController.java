package com.microservices.inventory.controller;

import com.microservices.inventory.dto.*;
import com.microservices.inventory.entity.Inventory;
import com.microservices.inventory.entity.InventoryReservation;
import com.microservices.inventory.mapper.InventoryMapper;
import com.microservices.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 庫存管理 REST API 控制器
 * 提供庫存查詢、預留、釋放、確認等功能
 * 
 * 實現需求:
 * - 需求 3.9: 庫存查詢功能
 * - 需求 3.4: 庫存不足保護機制
 */
@RestController
@RequestMapping("/inventory")
@Tag(name = "庫存管理", description = "庫存查詢、預留、釋放、確認和管理功能")
public class InventoryController {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    
    @Autowired
    private InventoryService inventoryService;
    
    /**
     * 獲取庫存服務資訊
     * 
     * @return 服務資訊和統計
     */
    @Operation(summary = "獲取庫存服務資訊", description = "獲取庫存服務的基本資訊和統計數據")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查詢成功",
                content = @Content(mediaType = "application/json"))
    })
    @GetMapping
    public ResponseEntity<Map<String, Object>> getServiceInfo() {
        logger.info("查詢庫存服務資訊");
        
        // 獲取低庫存產品數量
        long lowStockCount = inventoryService.findLowStockInventories().size();
        
        Map<String, Object> serviceInfo = Map.of(
            "serviceName", "Inventory Service",
            "version", "1.0.0",
            "status", "RUNNING",
            "timestamp", LocalDateTime.now(),
            "statistics", Map.of(
                "lowStockProducts", lowStockCount
            ),
            "endpoints", List.of(
                "GET /{productId} - 查詢產品庫存",
                "POST /{productId}/reserve - 臨時預留庫存",
                "POST /{productId}/release - 釋放預留庫存",
                "POST /{productId}/confirm - 確認預留",
                "PUT /{productId}/stock - 更新庫存數量",
                "GET /reservations - 查詢客戶預留記錄",
                "GET /{productId}/reservations - 查詢產品預留記錄",
                "GET /low-stock - 查詢低庫存產品",
                "POST /cleanup-expired - 清理過期預留"
            )
        );
        
        logger.info("庫存服務資訊查詢成功");
        return ResponseEntity.ok(serviceInfo);
    }
    
    /**
     * 查詢產品庫存資訊
     * 
     * @param productId 產品ID
     * @return 庫存資訊，包含可用、臨時預留和正式預留數量
     */
    @Operation(summary = "查詢產品庫存", description = "查詢指定產品的庫存資訊，包含可用庫存、臨時預留和正式預留數量")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查詢成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = InventoryDTO.class))),
        @ApiResponse(responseCode = "404", description = "產品庫存不存在")
    })
    @GetMapping("/{productId}")
    public ResponseEntity<InventoryDTO> getInventory(
            @Parameter(description = "產品 ID") @PathVariable("productId") Long productId) {
        logger.info("查詢產品庫存: productId={}", productId);
        
        Inventory inventory = inventoryService.findByProductId(productId)
                .orElseThrow(() -> new com.microservices.inventory.exception.InventoryNotFoundException(
                        "Inventory not found for productId: " + productId));
        InventoryDTO inventoryDTO = InventoryMapper.toDTO(inventory);
        logger.info("庫存查詢成功: productId={}, availableStock={}",
                productId, inventoryDTO.getAvailableStock());
        return ResponseEntity.ok(inventoryDTO);
    }
    
    /**
     * 臨時預留庫存（購物車階段）
     * 使用分散式鎖和樂觀鎖確保並發安全
     * 
     * @param productId 產品ID
     * @param request 預留請求資料
     * @return 預留記錄資訊
     */
    @Operation(summary = "臨時預留庫存", description = "為購物車階段臨時預留庫存，使用分散式鎖確保並發安全，預設30分鐘後過期")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "預留成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ReservationDTO.class))),
        @ApiResponse(responseCode = "400", description = "庫存不足或並發衝突"),
        @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @PostMapping("/{productId}/reserve")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReservationDTO> reserveInventory(
            @Parameter(description = "產品 ID") @PathVariable Long productId,
            @Parameter(description = "預留請求") @Valid @RequestBody ReserveInventoryRequest request) {
        
        logger.info("臨時預留庫存: productId={}, userId={}, quantity={}", 
                   productId, request.getUserId(), request.getQuantity());
        
        try {
            // 一律以 UTC LocalDateTime 存儲／比較，與 ReservationDTO 的 OffsetDateTime(UTC) 對齊
            LocalDateTime expiresAt = request.getExpiresAt() == null
                    ? LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(30)
                    : request.getExpiresAt().withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
            
            InventoryReservation reservation = inventoryService.reserveTemporary(
                productId, 
                request.getUserId(), 
                request.getQuantity(), 
                expiresAt
            );
            
            ReservationDTO reservationDTO = InventoryMapper.toDTO(reservation);
            logger.info("臨時預留成功: productId={}, userId={}, reservationId={}", 
                       productId, request.getUserId(), reservation.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(reservationDTO);
            
        } catch (InventoryService.InsufficientStockException e) {
            // 異常由 GlobalExceptionHandler 處理
            throw new RuntimeException(e);
        } catch (InventoryService.ConcurrentModificationException e) {
            // 異常由 GlobalExceptionHandler 處理
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 釋放預留庫存
     * 支援釋放臨時預留（購物車移除）和正式預留（訂單取消）
     * 
     * @param productId 產品ID
     * @param request 釋放請求資料
     * @return 成功回應
     */
    @Operation(summary = "釋放預留庫存", description = "釋放臨時預留（購物車移除）或正式預留（訂單取消）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "釋放成功"),
        @ApiResponse(responseCode = "400", description = "預留記錄不存在"),
        @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @PostMapping("/{productId}/release")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> releaseInventory(
            @Parameter(description = "產品 ID") @PathVariable Long productId,
            @Parameter(description = "釋放請求") @Valid @RequestBody ReleaseInventoryRequest request) {
        
        logger.info("釋放預留庫存: productId={}, userId={}, quantity={}, type={}", 
                   productId, request.getUserId(), request.getQuantity(), request.getReleaseType());
        
        try {
            if ("CONFIRMED".equalsIgnoreCase(request.getReleaseType())) {
                // 釋放正式預留（訂單取消）
                inventoryService.releaseConfirmedReservation(
                    productId, 
                    request.getUserId(), 
                    request.getQuantity()
                );
            } else {
                // 釋放臨時預留（購物車移除，預設行為）
                inventoryService.releaseTemporaryReservation(
                    productId, 
                    request.getUserId(), 
                    request.getQuantity()
                );
            }
            
            logger.info("預留釋放成功: productId={}, userId={}, type={}", 
                       productId, request.getUserId(), request.getReleaseType());
            
            return ResponseEntity.ok().build();
            
        } catch (InventoryService.ReservationNotFoundException e) {
            // 異常由 GlobalExceptionHandler 處理
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 確認預留（將臨時預留轉換為正式預留）
     * 在訂單創建時調用，使用分散式鎖確保原子性
     * 
     * @param productId 產品ID
     * @param request 確認請求資料
     * @return 確認後的預留記錄
     */
    @Operation(summary = "確認預留", description = "將臨時預留轉換為正式預留，在訂單創建時調用")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "確認成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ReservationDTO.class))),
        @ApiResponse(responseCode = "400", description = "預留記錄不存在或並發衝突"),
        @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @PostMapping("/{productId}/confirm")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReservationDTO> confirmReservation(
            @Parameter(description = "產品 ID") @PathVariable Long productId,
            @Parameter(description = "確認請求") @Valid @RequestBody ConfirmReservationRequest request) {
        
        logger.info("確認預留: productId={}, userId={}, quantity={}", 
                   productId, request.getUserId(), request.getQuantity());
        
        try {
            InventoryReservation confirmedReservation = inventoryService.confirmReservation(
                productId, 
                request.getUserId(), 
                request.getQuantity()
            );
            
            ReservationDTO reservationDTO = InventoryMapper.toDTO(confirmedReservation);
            logger.info("預留確認成功: productId={}, userId={}, reservationId={}", 
                       productId, request.getUserId(), confirmedReservation.getId());
            
            return ResponseEntity.ok(reservationDTO);
            
        } catch (InventoryService.ReservationNotFoundException e) {
            // 異常由 GlobalExceptionHandler 處理
            throw new RuntimeException(e);
        } catch (InventoryService.ConcurrentModificationException e) {
            // 異常由 GlobalExceptionHandler 處理
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 更新產品庫存數量
     * 
     * @param productId 產品ID
     * @param request 更新請求資料
     * @return 更新後的庫存資訊
     */
    @Operation(summary = "更新庫存數量", description = "更新指定產品的庫存數量")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = InventoryDTO.class))),
        @ApiResponse(responseCode = "400", description = "更新失敗"),
        @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @PutMapping("/{productId}/stock")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<InventoryDTO> updateStock(
            @Parameter(description = "產品 ID") @PathVariable Long productId,
            @Parameter(description = "更新請求") @Valid @RequestBody UpdateStockRequest request) {
        
        logger.info("更新庫存: productId={}, newStock={}", productId, request.getNewStock());
        
        try {
            Inventory updatedInventory = inventoryService.updateStock(productId, request.getNewStock());
            InventoryDTO inventoryDTO = InventoryMapper.toDTO(updatedInventory);
            
            logger.info("庫存更新成功: productId={}, newStock={}", productId, request.getNewStock());
            return ResponseEntity.ok(inventoryDTO);
            
        } catch (Exception e) {
            logger.error("庫存更新失敗: productId={}, error={}", productId, e.getMessage());
            throw e;
        }
    }

    /**
     * 建立產品庫存（若不存在）— 管理用途
     */
    @Operation(summary = "建立庫存", description = "為產品建立初始庫存記錄")
    @PostMapping("/{productId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<InventoryDTO> createInventory(
            @Parameter(description = "產品 ID") @PathVariable Long productId,
            @Parameter(description = "初始庫存") @Valid @RequestBody UpdateStockRequest request) {
        logger.info("建立庫存: productId={}, newStock={}", productId, request.getNewStock());
        Inventory created = inventoryService.createInventory(productId, request.getNewStock(), 5);
        return ResponseEntity.status(HttpStatus.CREATED).body(InventoryMapper.toDTO(created));
    }
    
    /**
     * 查詢客戶的所有預留記錄
     * 
     * @param customerId 客戶ID
     * @return 預留記錄列表
     */
    @Operation(summary = "查詢客戶預留記錄", description = "查詢指定客戶的所有預留記錄")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查詢成功",
                content = @Content(mediaType = "application/json", 
                array = @ArraySchema(schema = @Schema(implementation = ReservationDTO.class))))
    })
    @GetMapping("/reservations")
    public ResponseEntity<List<ReservationDTO>> getCustomerReservations(
            @Parameter(description = "客戶 ID") @RequestParam String customerId) {
        
        logger.info("查詢客戶預留記錄: customerId={}", customerId);
        
        List<InventoryReservation> reservations = inventoryService.findReservationsByUser(Long.valueOf(customerId));
        List<ReservationDTO> reservationDTOs = reservations.stream()
            .map(InventoryMapper::toDTO)
            .collect(Collectors.toList());
        
        logger.info("客戶預留記錄查詢成功: customerId={}, count={}", customerId, reservationDTOs.size());
        return ResponseEntity.ok(reservationDTOs);
    }
    
    /**
     * 查詢產品的所有預留記錄
     * 
     * @param productId 產品ID
     * @return 預留記錄列表
     */
    @Operation(summary = "查詢產品預留記錄", description = "查詢指定產品的所有預留記錄")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查詢成功",
                content = @Content(mediaType = "application/json", 
                array = @ArraySchema(schema = @Schema(implementation = ReservationDTO.class))))
    })
    @GetMapping("/{productId}/reservations")
    public ResponseEntity<List<ReservationDTO>> getProductReservations(
            @Parameter(description = "產品 ID") @PathVariable Long productId) {
        
        logger.info("查詢產品預留記錄: productId={}", productId);
        
        List<InventoryReservation> reservations = inventoryService.findReservationsByProduct(productId);
        List<ReservationDTO> reservationDTOs = reservations.stream()
            .map(InventoryMapper::toDTO)
            .collect(Collectors.toList());
        
        logger.info("產品預留記錄查詢成功: productId={}, count={}", productId, reservationDTOs.size());
        return ResponseEntity.ok(reservationDTOs);
    }
    
    /**
     * 查詢所有低庫存產品
     * 
     * @return 低庫存產品列表
     */
    @Operation(summary = "查詢低庫存產品", description = "查詢所有低於安全庫存量的產品")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查詢成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = InventoryDTO.class)))
    })
    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryDTO>> getLowStockInventories() {
        logger.info("查詢低庫存產品");
        
        List<Inventory> lowStockInventories = inventoryService.findLowStockInventories();
        List<InventoryDTO> inventoryDTOs = lowStockInventories.stream()
            .map(InventoryMapper::toDTO)
            .collect(Collectors.toList());
        
        logger.info("低庫存產品查詢成功: count={}", inventoryDTOs.size());
        return ResponseEntity.ok(inventoryDTOs);
    }
    
    /**
     * 清理過期的臨時預留
     * 
     * @return 清理的記錄數量
     */
    @Operation(summary = "清理過期預留", description = "清理所有過期的臨時預留記錄")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "清理完成",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/cleanup-expired")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Integer>> cleanupExpiredReservations() {
        logger.info("開始清理過期臨時預留");
        
        int cleanedCount = inventoryService.cleanupExpiredTemporaryReservations();
        
        Map<String, Integer> result = Map.of("cleanedCount", cleanedCount);
        logger.info("過期預留清理完成: cleanedCount={}", cleanedCount);
        
        return ResponseEntity.ok(result);
    }
}
