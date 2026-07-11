package com.microservices.order.controller;

import com.microservices.order.dto.OrderDTO;
import com.microservices.order.dto.PlaceOrderRequest;
import com.microservices.order.dto.UpdateOrderStatusRequest;
import com.microservices.common.api.PageResponse;
import com.microservices.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 訂單控制器
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "訂單管理", description = "訂單相關的 API 操作")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    @Autowired
    private OrderService orderService;
    
    /**
     * 下單（從購物車創建訂單）
     */
    @Operation(summary = "下單", description = "從客戶的購物車創建新訂單")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "成功創建訂單",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = OrderDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "購物車不存在或為空"),
        @ApiResponse(responseCode = "409", description = "庫存不足")
    })
    @PostMapping
    public ResponseEntity<OrderDTO> placeOrder(@RequestBody PlaceOrderRequest request,
                                             HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Authentication is required");
        }
        request.setUserId(userId);
        
        logger.info("下單請求: userId={}", userId);
        
        OrderDTO order = orderService.placeOrder(request);
        
        logger.info("成功創建訂單: orderNumber={}, userId={}, totalAmount={}", 
                   order.getOrderNumber(), order.getUserId(), order.getTotalAmount());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
    
    /**
     * 根據ID獲取訂單
     */
    @Operation(summary = "根據ID獲取訂單", description = "根據訂單ID獲取訂單詳細資訊")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功獲取訂單",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = OrderDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "訂單不存在")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDTO> getOrder(
            @Parameter(description = "訂單ID", required = true) @PathVariable Long orderId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        logger.info("獲取訂單請求: orderId={}, userId={}", orderId, userId);
        
        OrderDTO order = orderService.getOrder(orderId);
        if (userId == null || !userId.equals(order.getUserId())) {
            logger.warn("拒絕越權讀取訂單: orderId={}, requester={}, owner={}",
                    orderId, userId, order.getUserId());
            throw new org.springframework.security.access.AccessDeniedException("Cannot access another user's order");
        }
        
        logger.info("成功獲取訂單: orderId={}, orderNumber={}, status={}", 
                   orderId, order.getOrderNumber(), order.getStatus());
        
        return ResponseEntity.ok(order);
    }
    
    /**
     * 根據訂單號獲取訂單
     */
    @Operation(summary = "根據訂單號獲取訂單", description = "根據訂單號獲取訂單詳細資訊")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功獲取訂單",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = OrderDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "訂單不存在")
    })
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderDTO> getOrderByNumber(
            @Parameter(description = "訂單號", required = true) @PathVariable String orderNumber,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        logger.info("根據訂單號獲取訂單請求: orderNumber={}, userId={}", orderNumber, userId);
        
        OrderDTO order = orderService.getOrderByNumber(orderNumber);
        if (userId == null || !userId.equals(order.getUserId())) {
            logger.warn("拒絕越權讀取訂單: orderNumber={}, requester={}, owner={}",
                    orderNumber, userId, order.getUserId());
            throw new org.springframework.security.access.AccessDeniedException("Cannot access another user's order");
        }
        
        logger.info("成功根據訂單號獲取訂單: orderNumber={}, customerId={}, status={}", 
                   orderNumber, order.getUserId(), order.getStatus());
        
        return ResponseEntity.ok(order);
    }
    
    /**
     * 獲取客戶訂單列表
     */
    @Operation(summary = "獲取客戶訂單列表", description = "分頁獲取指定客戶的訂單列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功獲取訂單列表",
                content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效")
    })
    @GetMapping
    public ResponseEntity<PageResponse<OrderDTO>> getUserOrders(
            HttpServletRequest httpRequest,
            @Parameter(description = "分頁參數") @PageableDefault(size = 20) Pageable pageable) {
        
        Long userId = (Long) httpRequest.getAttribute("userId");
        
        logger.info("獲取客戶訂單列表請求: userId={}, page={}, size={}", 
                   userId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<OrderDTO> orders = orderService.getUserOrders(userId, pageable);
        
        logger.info("成功獲取客戶訂單列表: userId={}, totalOrders={}, currentPageSize={}", 
                   userId, orders.getTotalElements(), orders.getNumberOfElements());
        
        return ResponseEntity.ok(PageResponse.from(orders));
    }
    
    /**
     * 取消訂單
     */
    @Operation(summary = "取消訂單", description = "取消指定的訂單並恢復庫存")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功取消訂單",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = OrderDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "訂單不存在"),
        @ApiResponse(responseCode = "409", description = "訂單狀態不允許取消")
    })
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDTO> cancelOrder(
            @Parameter(description = "訂單ID", required = true) @PathVariable Long orderId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        logger.info("取消訂單請求: orderId={}, userId={}", orderId, userId);

        OrderDTO existing = orderService.getOrder(orderId);
        if (userId == null || !userId.equals(existing.getUserId())) {
            logger.warn("拒絕越權取消訂單: orderId={}, requester={}, owner={}",
                    orderId, userId, existing.getUserId());
            throw new org.springframework.security.access.AccessDeniedException("Cannot cancel another user's order");
        }
        
        OrderDTO order = orderService.cancelOrder(orderId);
        
        logger.info("成功取消訂單: orderId={}, orderNumber={}", orderId, order.getOrderNumber());
        
        return ResponseEntity.ok(order);
    }
    
    /**
     * 更新訂單狀態
     */
    @Operation(summary = "更新訂單狀態", description = "更新指定訂單的狀態")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功更新訂單狀態",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = OrderDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "訂單不存在"),
        @ApiResponse(responseCode = "409", description = "訂單狀態轉換無效")
    })
    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @Parameter(description = "訂單ID", required = true) @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        logger.info("更新訂單狀態請求: orderId={}, newStatus={}, userId={}, role={}",
                orderId, request.getStatus(), userId, role);

        if (!("ADMIN".equals(role) || "OPERATOR".equals(role))) {
            logger.warn("拒絕非營運角色更新訂單狀態: orderId={}, requester={}, role={}",
                    orderId, userId, role);
            throw new org.springframework.security.access.AccessDeniedException(
                    "ADMIN or OPERATOR role is required to update order status");
        }
        
        OrderDTO order = orderService.updateOrderStatus(orderId, request);
        
        logger.info("成功更新訂單狀態: orderId={}, orderNumber={}, status={}", 
                   orderId, order.getOrderNumber(), order.getStatus());
        
        return ResponseEntity.ok(order);
    }
    
    /**
     * 檢查訂單號是否存在
     */
    @Operation(summary = "檢查訂單號是否存在", description = "檢查指定訂單號是否已存在")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功檢查訂單號存在狀態",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = Boolean.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效")
    })
    @GetMapping("/exists")
    public ResponseEntity<Boolean> orderExists(
            @Parameter(description = "訂單號", required = true) @RequestParam String orderNumber) {
        logger.info("檢查訂單號是否存在請求: orderNumber={}", orderNumber);
        
        boolean exists = orderService.orderExists(orderNumber);
        
        logger.info("訂單號存在檢查結果: orderNumber={}, exists={}", orderNumber, exists);
        
        return ResponseEntity.ok(exists);
    }
}
