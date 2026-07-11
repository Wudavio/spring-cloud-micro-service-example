package com.microservices.auth.controller;

import com.microservices.auth.dto.UserDTO;
import com.microservices.auth.entity.User;
import com.microservices.auth.entity.UserRole;
import com.microservices.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * 用戶管理控制器
 */
@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "*")
@Tag(name = "用戶管理", description = "用戶管理相關的 API 操作")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    /**
     * 根據ID獲取用戶
     */
    @Operation(summary = "根據ID獲取用戶", description = "根據用戶ID獲取用戶詳細資訊")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功獲取用戶",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = UserDTO.class))),
        @ApiResponse(responseCode = "404", description = "用戶不存在")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUser(
            @Parameter(description = "用戶ID", required = true) @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest request) {
        logger.info("獲取用戶請求: userId={}", userId);

        if (!canAccessUser(authentication, request, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return userService.findById(userId)
                .map(user -> {
                    logger.info("成功獲取用戶: userId={}, username={}", userId, user.getUsername());
                    return ResponseEntity.ok(user);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 獲取所有用戶（分頁）— 僅 ADMIN
     */
    @Operation(summary = "獲取所有用戶", description = "分頁獲取所有用戶列表（ADMIN）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功獲取用戶列表")
    })
    @GetMapping
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        logger.info("獲取用戶列表請求: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        
        Page<UserDTO> users = userService.getAllUsers(pageable);
        
        logger.info("成功獲取用戶列表: totalElements={}, totalPages={}", 
                   users.getTotalElements(), users.getTotalPages());
        
        return ResponseEntity.ok(users);
    }
    
    /**
     * 搜索用戶 — 僅 ADMIN
     */
    @Operation(summary = "搜索用戶", description = "根據用戶名搜索用戶（ADMIN）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "搜索成功")
    })
    @GetMapping("/search")
    public ResponseEntity<Page<UserDTO>> searchUsers(
            @Parameter(description = "搜索關鍵字") @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        logger.info("搜索用戶請求: keyword={}", keyword);
        
        Page<UserDTO> users = userService.searchByUsername(keyword, pageable);
        
        logger.info("搜索用戶完成: keyword={}, found={}", keyword, users.getTotalElements());
        
        return ResponseEntity.ok(users);
    }
    
    /**
     * 更新用戶資訊 — 本人或 ADMIN；role/status 僅 ADMIN 可改
     */
    @Operation(summary = "更新用戶資訊", description = "更新指定用戶的資訊")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = UserDTO.class))),
        @ApiResponse(responseCode = "404", description = "用戶不存在")
    })
    @PutMapping("/{userId}")
    public ResponseEntity<UserDTO> updateUser(
            @Parameter(description = "用戶ID", required = true) @PathVariable Long userId,
            @RequestBody UserDTO userDTO,
            Authentication authentication,
            HttpServletRequest request) {
        logger.info("更新用戶請求: userId={}", userId);

        if (!canAccessUser(authentication, request, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean admin = isAdmin(authentication);
        if (!admin) {
            // 非管理員禁止提升權限或改狀態
            userDTO.setRole(null);
            userDTO.setStatus(null);
        }
        
        UserDTO updatedUser = userService.updateUser(userId, userDTO);
        
        logger.info("用戶更新成功: userId={}", userId);
        
        return ResponseEntity.ok(updatedUser);
    }
    
    /**
     * 刪除用戶 — 僅 ADMIN
     */
    @Operation(summary = "刪除用戶", description = "刪除指定的用戶（ADMIN）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "刪除成功"),
        @ApiResponse(responseCode = "404", description = "用戶不存在")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "用戶ID", required = true) @PathVariable Long userId,
            Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        logger.info("刪除用戶請求: userId={}", userId);
        
        userService.deleteUser(userId);
        
        logger.info("用戶刪除成功: userId={}", userId);
        
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean canAccessUser(Authentication authentication, HttpServletRequest request, Long targetUserId) {
        if (isAdmin(authentication)) {
            return true;
        }
        Object attr = request.getAttribute("userId");
        if (attr instanceof Long currentId) {
            return currentId.equals(targetUserId);
        }
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getUserId().equals(targetUserId);
        }
        return false;
    }
}
