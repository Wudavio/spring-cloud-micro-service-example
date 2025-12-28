package com.microservices.auth.controller;

import com.microservices.auth.dto.AuthResponse;
import com.microservices.auth.dto.LoginRequest;
import com.microservices.auth.dto.RegisterRequest;
import com.microservices.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
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
 * 認證控制器
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Tag(name = "認證管理", description = "用戶認證相關的 API 操作")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private AuthService authService;
    
    /**
     * 用戶註冊
     */
    @Operation(summary = "用戶註冊", description = "註冊新用戶帳號")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "註冊成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "409", description = "用戶名或郵箱已存在")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("用戶註冊請求: username={}, email={}", request.getUsername(), request.getEmail());
        
        AuthResponse response = authService.register(request);
        
        logger.info("用戶註冊成功: userId={}, username={}", 
                   response.getUser().getUserId(), response.getUser().getUsername());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * 用戶登入
     */
    @Operation(summary = "用戶登入", description = "用戶登入驗證")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "登入成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "請求參數無效"),
        @ApiResponse(responseCode = "401", description = "用戶名或密碼錯誤")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        logger.info("用戶登入請求: usernameOrEmail={}", request.getUsernameOrEmail());
        
        AuthResponse response = authService.login(request);
        
        logger.info("用戶登入成功: userId={}, username={}", 
                   response.getUser().getUserId(), response.getUser().getUsername());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 驗證 token
     */
    @Operation(summary = "驗證 Token", description = "驗證 JWT token 的有效性")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token 有效"),
        @ApiResponse(responseCode = "401", description = "Token 無效或已過期")
    })
    @PostMapping("/validate")
    public ResponseEntity<Void> validateToken(@RequestHeader("Authorization") String authHeader) {
        logger.debug("Token 驗證請求");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String token = authHeader.substring(7);
        boolean isValid = authService.validateToken(token);
        
        if (isValid) {
            logger.debug("Token 驗證成功");
            return ResponseEntity.ok().build();
        } else {
            logger.debug("Token 驗證失敗");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    /**
     * 獲取當前用戶ID
     */
    @Operation(summary = "獲取用戶ID", description = "從 JWT token 中獲取用戶ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功獲取用戶ID"),
        @ApiResponse(responseCode = "401", description = "Token 無效或已過期")
    })
    @GetMapping("/user-id")
    public ResponseEntity<Long> getUserId(@RequestHeader("Authorization") String authHeader) {
        logger.debug("獲取用戶ID請求");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String token = authHeader.substring(7);
        
        if (!authService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        Long userId = authService.getUserIdFromToken(token);
        
        logger.debug("成功獲取用戶ID: {}", userId);
        
        return ResponseEntity.ok(userId);
    }
}