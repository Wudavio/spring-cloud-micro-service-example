package com.microservices.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * API Gateway Security 配置
 * 允許 Swagger UI 和 API 文檔的訪問
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                        // 允許 Swagger UI 和 API 文檔
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/v3/api-docs").permitAll()
                        .pathMatchers("/swagger-resources/**").permitAll()
                        .pathMatchers("/webjars/**").permitAll()
                        // 允許 Actuator 健康檢查
                        .pathMatchers("/actuator/**").permitAll()
                        // 允許所有 API 請求（由各個服務自己處理認證）
                        .pathMatchers("/api/**").permitAll()
                        // 其他請求允許訪問
                        .anyExchange().permitAll()
                )
                .build();
    }
}