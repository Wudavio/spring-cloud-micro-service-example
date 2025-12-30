package com.microservices.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置 - 允許 Swagger 端點公開訪問
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // 允許 Swagger UI 和 API 文檔
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/v3/api-docs/**", "/v3/api-docs").permitAll()
                .requestMatchers("/swagger-resources/**").permitAll()
                // 允許 Actuator 健康檢查
                .requestMatchers("/actuator/**").permitAll()
                // 允許庫存服務的 API 端點（注意：Gateway 會 strip 掉 /api 前綴）
                .requestMatchers("/inventory/**").permitAll()
                .requestMatchers("/api/inventory/**").permitAll() // 保留原有配置以防直接訪問
                // 其他請求需要認證
                .anyRequest().authenticated()
            );
        
        return http.build();
    }
}