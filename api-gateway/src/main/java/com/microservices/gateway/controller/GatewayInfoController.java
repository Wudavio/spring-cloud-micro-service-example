package com.microservices.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/gateway")
@Tag(name = "Gateway Info", description = "API Gateway 資訊")
public class GatewayInfoController {

    @GetMapping("/info")
    @Operation(summary = "獲取 Gateway 資訊", description = "返回 API Gateway 的基本資訊")
    public Mono<Map<String, Object>> getGatewayInfo() {
        Map<String, Object> info = Map.of(
                "name", "Microservices API Gateway",
                "version", "1.0.0",
                "description", "統一的微服務 API 入口",
                "services", new String[]{
                        "auth-service",
                        "product-service", 
                        "inventory-service",
                        "order-service"
                }
        );
        
        return Mono.just(info);
    }

    @GetMapping("/health")
    @Operation(summary = "健康檢查", description = "檢查 API Gateway 的健康狀態")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP", "timestamp", String.valueOf(System.currentTimeMillis())));
    }
}