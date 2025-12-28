package com.microservices.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用戶登入請求
 */
public class LoginRequest {
    
    @NotBlank(message = "用戶名或郵箱不能為空")
    private String usernameOrEmail;
    
    @NotBlank(message = "密碼不能為空")
    private String password;
    
    // 預設建構子
    public LoginRequest() {}
    
    // 建構子
    public LoginRequest(String usernameOrEmail, String password) {
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
    }
    
    // Getters and Setters
    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }
    
    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}