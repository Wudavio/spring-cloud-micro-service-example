package com.microservices.order.controller;

import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.dto.CartDTO;
import com.microservices.order.dto.UpdateCartItemRequest;
import com.microservices.order.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 購物車控制器
 */
@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
@Tag(name = "購物車管理", description = "購物車相關的 API 操作")
public class CartController {
    
    private static final Logger logger = LoggerFactory.getLogger(CartController.class);
    
    @Autowired
    private CartService cartService;
    
    /**
     * 添加商品到購物車
     */
    @Operation(summary = "添加商品到購物車", description = "將指定商品添加到客戶的購物車中")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "成功添加商品到購物車",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CartDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "商品不存在"),
        @ApiResponse(responseCode = "409", description = "庫存不足")
    })
    @PostMapping("/items")
    public ResponseEntity<CartDTO> addToCart(@Valid @RequestBody AddToCartRequest request) {
        logger.info("添加商品到購物車請求: customerId={}, productId={}, quantity={}", 
                   request.getCustomerId(), request.getProductId(), request.getQuantity());
        
        CartDTO cart = cartService.addToCart(request);
        
        logger.info("成功添加商品到購物車: customerId={}, cartItemCount={}", 
                   request.getCustomerId(), cart.getItems().size());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }
    
    /**
     * 更新購物車項目數量
     */
    @Operation(summary = "更新購物車項目數量", description = "更新購物車中指定項目的數量")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功更新購物車項目",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CartDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "購物車項目不存在"),
        @ApiResponse(responseCode = "409", description = "庫存不足")
    })
    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartDTO> updateCartItem(
            @Parameter(description = "購物車項目ID", required = true) @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        
        logger.info("更新購物車項目請求: itemId={}, customerId={}, quantity={}", 
                   itemId, request.getCustomerId(), request.getQuantity());
        
        CartDTO cart = cartService.updateCartItem(itemId, request);
        
        logger.info("成功更新購物車項目: itemId={}, newQuantity={}", itemId, request.getQuantity());
        
        return ResponseEntity.ok(cart);
    }
    
    /**
     * 從購物車移除商品
     */
    @Operation(summary = "從購物車移除商品", description = "從購物車中移除指定的商品項目")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功移除商品",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CartDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "購物車項目不存在")
    })
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartDTO> removeFromCart(
            @Parameter(description = "購物車項目ID", required = true) @PathVariable Long itemId,
            @Parameter(description = "客戶ID", required = true) @RequestParam String customerId) {
        
        logger.info("從購物車移除商品請求: itemId={}, customerId={}", itemId, customerId);
        
        CartDTO cart = cartService.removeFromCart(itemId, customerId);
        
        logger.info("成功從購物車移除商品: itemId={}", itemId);
        
        return ResponseEntity.ok(cart);
    }
    
    /**
     * 獲取購物車
     */
    @Operation(summary = "獲取購物車", description = "根據客戶ID獲取購物車詳細資訊")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功獲取購物車",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CartDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "購物車不存在")
    })
    @GetMapping
    public ResponseEntity<CartDTO> getCart(
            @Parameter(description = "客戶ID", required = true) @RequestParam String customerId) {
        logger.info("獲取購物車請求: customerId={}", customerId);
        
        CartDTO cart = cartService.getCart(customerId);
        
        logger.info("成功獲取購物車: customerId={}, itemCount={}", 
                   customerId, cart.getItems().size());
        
        return ResponseEntity.ok(cart);
    }
    
    /**
     * 清空購物車
     */
    @Operation(summary = "清空購物車", description = "清空指定客戶的購物車中所有商品")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "成功清空購物車"),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "404", description = "購物車不存在")
    })
    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            @Parameter(description = "客戶ID", required = true) @RequestParam String customerId) {
        logger.info("清空購物車請求: customerId={}", customerId);
        
        cartService.clearCart(customerId);
        
        logger.info("成功清空購物車: customerId={}", customerId);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 檢查購物車是否存在
     */
    @Operation(summary = "檢查購物車是否存在", description = "檢查指定客戶是否有購物車")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功檢查購物車存在狀態",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = Boolean.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效")
    })
    @GetMapping("/exists")
    public ResponseEntity<Boolean> cartExists(
            @Parameter(description = "客戶ID", required = true) @RequestParam String customerId) {
        logger.info("檢查購物車是否存在請求: customerId={}", customerId);
        
        boolean exists = cartService.cartExists(customerId);
        
        logger.info("購物車存在檢查結果: customerId={}, exists={}", customerId, exists);
        
        return ResponseEntity.ok(exists);
    }
}