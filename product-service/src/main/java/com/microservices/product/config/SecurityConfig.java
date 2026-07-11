package com.microservices.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 產品服務安全配置。
 * GET 公開（供 Feign 查詢）；寫入由 {@link AdminWriteGuardFilter} 驗證 JWT 簽章與 ADMIN 角色。
 * Actuator 僅放行 health/info；其餘管理端點不對外。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/v3/api-docs/**", "/v3/api-docs").permitAll()
                .requestMatchers("/swagger-resources/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/products/**", "/api/products/**").permitAll()
                // 寫入由 AdminWriteGuardFilter 強制 ADMIN + JWT 驗簽
                .requestMatchers(HttpMethod.POST, "/products/**", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/products/**", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/products/**", "/api/products/**").permitAll()
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
}
