package com.microservices.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 聚合配置
 * 通過 application.yml 中的 springdoc.swagger-ui.urls 配置來聚合各服務的 API 文檔
 */
@Configuration
public class SwaggerUiConfig {
    // 配置通過 application.yml 完成
    // 這個類主要用於未來可能的自定義配置
}