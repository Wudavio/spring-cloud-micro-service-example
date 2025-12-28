package com.microservices.order.service.impl;

import com.microservices.order.client.InventoryServiceClient;
import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.dto.AddToCartRequest;
import com.microservices.order.dto.CartDTO;
import com.microservices.order.dto.CartItemDTO;
import com.microservices.order.dto.UpdateCartItemRequest;
import com.microservices.order.entity.Cart;
import com.microservices.order.entity.CartItem;
import com.microservices.order.exception.CartNotFoundException;
import com.microservices.order.exception.InsufficientInventoryException;
import com.microservices.order.exception.ProductNotFoundException;
import com.microservices.order.mapper.CartMapper;
import com.microservices.order.repository.CartItemRepository;
import com.microservices.order.repository.CartRepository;
import com.microservices.order.service.CartService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 購物車服務實現
 */
@Service
@Transactional
public class CartServiceImpl implements CartService {
    
    private static final Logger logger = LoggerFactory.getLogger(CartServiceImpl.class);
    
    @Autowired
    private CartRepository cartRepository;
    
    @Autowired
    private CartItemRepository cartItemRepository;
    
    @Autowired
    private CartMapper cartMapper;
    
    @Autowired
    private ProductServiceClient productServiceClient;
    
    @Autowired
    private InventoryServiceClient inventoryServiceClient;
    
    @Override
    public CartDTO addToCart(AddToCartRequest request) {
        logger.info("添加商品到購物車: customerId={}, productId={}, quantity={}", 
                   request.getCustomerId(), request.getProductId(), request.getQuantity());
        
        // 1. 驗證產品存在性
        ProductServiceClient.ProductDTO product = validateProduct(request.getProductId());
        
        // 2. 獲取或創建購物車
        Cart cart = getOrCreateCart(request.getCustomerId());
        
        // 3. 檢查購物車中是否已有該商品
        Optional<CartItem> existingItem = cartItemRepository
            .findByCartIdAndProductId(cart.getId(), request.getProductId());
        
        if (existingItem.isPresent()) {
            // 更新現有項目數量
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            
            // 調整庫存預留
            adjustInventoryReservation(request.getProductId(), request.getCustomerId(), 
                                     item.getQuantity(), newQuantity);
            
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            // 預留庫存
            reserveInventory(request.getProductId(), request.getCustomerId(), request.getQuantity());
            
            // 創建新的購物車項目
            CartItem newItem = new CartItem(request.getProductId(), request.getQuantity(), product.getPrice());
            cart.addItem(newItem);
            cartItemRepository.save(newItem);
        }
        
        cartRepository.save(cart);
        
        // 4. 返回更新後的購物車
        Cart updatedCart = cartRepository.findByCustomerIdWithItems(request.getCustomerId())
            .orElseThrow(() -> new CartNotFoundException("購物車未找到"));
        
        CartDTO cartDTO = cartMapper.toDTO(updatedCart);
        enrichCartWithProductInfo(cartDTO);
        
        logger.info("成功添加商品到購物車: customerId={}, productId={}", 
                   request.getCustomerId(), request.getProductId());
        
        return cartDTO;
    }
    
    @Override
    public CartDTO updateCartItem(Long itemId, UpdateCartItemRequest request) {
        logger.info("更新購物車項目: itemId={}, customerId={}, quantity={}", 
                   itemId, request.getCustomerId(), request.getQuantity());
        
        // 1. 查找購物車項目
        CartItem item = cartItemRepository.findById(itemId)
            .orElseThrow(() -> new CartNotFoundException("購物車項目未找到"));
        
        // 2. 驗證客戶權限
        if (!item.getCart().getCustomerId().equals(request.getCustomerId())) {
            throw new CartNotFoundException("無權限操作此購物車項目");
        }
        
        // 3. 調整庫存預留
        adjustInventoryReservation(item.getProductId(), request.getCustomerId(), 
                                 item.getQuantity(), request.getQuantity());
        
        // 4. 更新項目數量
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        
        // 5. 返回更新後的購物車
        Cart cart = cartRepository.findByCustomerIdWithItems(request.getCustomerId())
            .orElseThrow(() -> new CartNotFoundException("購物車未找到"));
        
        CartDTO cartDTO = cartMapper.toDTO(cart);
        enrichCartWithProductInfo(cartDTO);
        
        logger.info("成功更新購物車項目: itemId={}, newQuantity={}", itemId, request.getQuantity());
        
        return cartDTO;
    }
    
    @Override
    public CartDTO removeFromCart(Long itemId, String customerId) {
        logger.info("從購物車移除商品: itemId={}, customerId={}", itemId, customerId);
        
        // 1. 查找購物車項目
        CartItem item = cartItemRepository.findById(itemId)
            .orElseThrow(() -> new CartNotFoundException("購物車項目未找到"));
        
        // 2. 驗證客戶權限
        if (!item.getCart().getCustomerId().equals(customerId)) {
            throw new CartNotFoundException("無權限操作此購物車項目");
        }
        
        // 3. 釋放庫存預留
        releaseInventory(item.getProductId(), customerId, item.getQuantity());
        
        // 4. 移除項目
        cartItemRepository.delete(item);
        
        // 5. 返回更新後的購物車
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId)
            .orElseThrow(() -> new CartNotFoundException("購物車未找到"));
        
        CartDTO cartDTO = cartMapper.toDTO(cart);
        enrichCartWithProductInfo(cartDTO);
        
        logger.info("成功從購物車移除商品: itemId={}", itemId);
        
        return cartDTO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public CartDTO getCart(String customerId) {
        logger.info("獲取購物車: customerId={}", customerId);
        
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId)
            .orElse(new Cart(customerId)); // 如果不存在則返回空購物車
        
        CartDTO cartDTO = cartMapper.toDTO(cart);
        enrichCartWithProductInfo(cartDTO);
        
        return cartDTO;
    }
    
    @Override
    public void clearCart(String customerId) {
        logger.info("清空購物車: customerId={}", customerId);
        
        Optional<Cart> cartOpt = cartRepository.findByCustomerIdWithItems(customerId);
        if (cartOpt.isPresent()) {
            Cart cart = cartOpt.get();
            
            // 釋放所有庫存預留
            for (CartItem item : cart.getItems()) {
                releaseInventory(item.getProductId(), customerId, item.getQuantity());
            }
            
            // 刪除購物車
            cartRepository.delete(cart);
            
            logger.info("成功清空購物車: customerId={}", customerId);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean cartExists(String customerId) {
        return cartRepository.existsByCustomerId(customerId);
    }
    
    /**
     * 驗證產品存在性
     */
    private ProductServiceClient.ProductDTO validateProduct(Long productId) {
        try {
            ProductServiceClient.ProductDTO product = productServiceClient.getProduct(productId);
            if (!"ACTIVE".equals(product.getStatus())) {
                throw new ProductNotFoundException("產品不可用: " + productId);
            }
            return product;
        } catch (FeignException.NotFound e) {
            throw new ProductNotFoundException("產品不存在: " + productId);
        } catch (FeignException e) {
            logger.error("調用產品服務失敗: productId={}", productId, e);
            throw new RuntimeException("產品服務不可用");
        }
    }
    
    /**
     * 獲取或創建購物車
     */
    private Cart getOrCreateCart(String customerId) {
        return cartRepository.findByCustomerId(customerId)
            .orElseGet(() -> {
                Cart newCart = new Cart(customerId);
                return cartRepository.save(newCart);
            });
    }
    
    /**
     * 預留庫存
     */
    private void reserveInventory(Long productId, String customerId, Integer quantity) {
        try {
            InventoryServiceClient.ReserveInventoryRequest request = 
                new InventoryServiceClient.ReserveInventoryRequest(customerId, quantity, "TEMPORARY");
            inventoryServiceClient.reserveInventory(productId, request);
        } catch (FeignException.BadRequest e) {
            throw new InsufficientInventoryException("庫存不足: productId=" + productId);
        } catch (FeignException e) {
            logger.error("調用庫存服務失敗: productId={}", productId, e);
            throw new RuntimeException("庫存服務不可用");
        }
    }
    
    /**
     * 調整庫存預留
     */
    private void adjustInventoryReservation(Long productId, String customerId, 
                                          Integer oldQuantity, Integer newQuantity) {
        int difference = newQuantity - oldQuantity;
        
        if (difference > 0) {
            // 需要增加預留
            reserveInventory(productId, customerId, difference);
        } else if (difference < 0) {
            // 需要減少預留
            releaseInventory(productId, customerId, Math.abs(difference));
        }
        // difference == 0 時不需要調整
    }
    
    /**
     * 釋放庫存預留
     */
    private void releaseInventory(Long productId, String customerId, Integer quantity) {
        try {
            InventoryServiceClient.ReleaseInventoryRequest request = 
                new InventoryServiceClient.ReleaseInventoryRequest(customerId, quantity, "TEMPORARY");
            inventoryServiceClient.releaseInventory(productId, request);
        } catch (FeignException e) {
            logger.error("釋放庫存失敗: productId={}, customerId={}, quantity={}", 
                        productId, customerId, quantity, e);
            // 釋放庫存失敗不應該阻止購物車操作，只記錄錯誤
        }
    }
    
    /**
     * 豐富購物車資訊（添加產品名稱等）
     */
    private void enrichCartWithProductInfo(CartDTO cartDTO) {
        if (cartDTO.getItems() != null) {
            for (CartItemDTO item : cartDTO.getItems()) {
                try {
                    ProductServiceClient.ProductDTO product = productServiceClient.getProduct(item.getProductId());
                    item.setProductName(product.getName());
                } catch (FeignException e) {
                    logger.warn("獲取產品資訊失敗: productId={}", item.getProductId());
                    item.setProductName("未知產品");
                }
            }
        }
    }
}