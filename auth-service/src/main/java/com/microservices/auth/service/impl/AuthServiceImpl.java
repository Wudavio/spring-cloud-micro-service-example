package com.microservices.auth.service.impl;

import com.microservices.auth.config.JwtUtil;
import com.microservices.auth.dto.AuthResponse;
import com.microservices.auth.dto.LoginRequest;
import com.microservices.auth.dto.RegisterRequest;
import com.microservices.auth.dto.UserDTO;
import com.microservices.auth.entity.User;
import com.microservices.auth.service.AuthService;
import com.microservices.auth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * 認證服務實現
 */
@Service
public class AuthServiceImpl implements AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Override
    public AuthResponse register(RegisterRequest request) {
        logger.info("用戶註冊請求: username={}", request.getUsername());
        
        // 註冊用戶
        UserDTO userDTO = userService.register(request);
        
        // 獲取用戶實體以生成 token
        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("註冊後無法找到用戶"));
        
        // 生成 JWT token
        String token = jwtUtil.generateToken(user, user.getUserId());
        
        logger.info("用戶註冊成功: userId={}, username={}", userDTO.getUserId(), userDTO.getUsername());
        
        return authResponse(token, userDTO);
    }
    
    @Override
    public AuthResponse login(LoginRequest request) {
        logger.info("用戶登入請求: usernameOrEmail={}", request.getUsernameOrEmail());
        
        try {
            // 認證用戶
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getUsernameOrEmail(),
                    request.getPassword()
                )
            );
            
            // 獲取認證後的用戶
            User user = (User) authentication.getPrincipal();
            
            // 生成 JWT token
            String token = jwtUtil.generateToken(user, user.getUserId());
            
            // 轉換為 DTO
            UserDTO userDTO = userService.convertToDTO(user);
            
            logger.info("用戶登入成功: userId={}, username={}", user.getUserId(), user.getUsername());
            
            return authResponse(token, userDTO);
            
        } catch (AuthenticationException e) {
            logger.error("用戶登入失敗: usernameOrEmail={}, error={}", request.getUsernameOrEmail(), e.getMessage());
            throw new BadCredentialsException("用戶名或密碼錯誤");
        }
    }

    private AuthResponse authResponse(String token, UserDTO user) {
        long expiresIn = jwtUtil.getExpirationSeconds();
        return new AuthResponse(token, user, expiresIn,
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(expiresIn));
    }
    
    @Override
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }
    
    @Override
    public Long getUserIdFromToken(String token) {
        return jwtUtil.getUserIdFromToken(token);
    }
    
    @Override
    public String getUsernameFromToken(String token) {
        return jwtUtil.getUsernameFromToken(token);
    }
}
