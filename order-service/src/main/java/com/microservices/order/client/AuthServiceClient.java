package com.microservices.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 認證服務客戶端
 */
@FeignClient(name = "auth-service", path = "/auth")
public interface AuthServiceClient {
    
    /**
     * 驗證 token 並獲取用戶ID
     */
    @GetMapping("/user-id")
    Long getUserId(@RequestHeader("Authorization") String authHeader);
    
    /**
     * 驗證 token（auth-service 實作為 POST）
     */
    @org.springframework.web.bind.annotation.PostMapping("/validate")
    void validateToken(@RequestHeader("Authorization") String authHeader);
}