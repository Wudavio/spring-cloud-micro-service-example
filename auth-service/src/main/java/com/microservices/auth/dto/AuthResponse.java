package com.microservices.auth.dto;

import java.time.OffsetDateTime;

/**
 * 認證響應
 */
public class AuthResponse {
    
    private String token;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private OffsetDateTime expiresAt;
    private UserDTO user;
    
    // 預設建構子
    public AuthResponse() {}
    
    // 建構子
    public AuthResponse(String token, UserDTO user, Long expiresIn, OffsetDateTime expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresIn = expiresIn;
        this.expiresAt = expiresAt;
    }
    
    // Getters and Setters
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    
    public UserDTO getUser() {
        return user;
    }
    
    public void setUser(UserDTO user) {
        this.user = user;
    }
}
