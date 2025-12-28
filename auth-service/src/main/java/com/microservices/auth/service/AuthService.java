package com.microservices.auth.service;

import com.microservices.auth.dto.AuthResponse;
import com.microservices.auth.dto.LoginRequest;
import com.microservices.auth.dto.RegisterRequest;

/**
 * 認證服務介面
 */
public interface AuthService {
    
    /**
     * 用戶註冊
     */
    AuthResponse register(RegisterRequest request);
    
    /**
     * 用戶登入
     */
    AuthResponse login(LoginRequest request);
    
    /**
     * 驗證 token
     */
    boolean validateToken(String token);
    
    /**
     * 從 token 獲取用戶ID
     */
    Long getUserIdFromToken(String token);
    
    /**
     * 從 token 獲取用戶名
     */
    String getUsernameFromToken(String token);
}