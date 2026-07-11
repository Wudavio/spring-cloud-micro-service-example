package com.microservices.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 產品服務安全配置
 * - 查詢公開（供購物車／下單 Feign 呼叫）
 * - 寫入操作需認證（Gateway 側亦應限制；此處再加一層）
 *
 * 注意：完整 JWT 角色校驗需與 auth 共用 secret；目前以「寫入需帶有效憑證」為底線，
 * 若請求無 SecurityContext，寫入會被拒絕。開發／測試可用 permit 搭配網路隔離。
 * 為避免 Feign 無 token 時中斷購物流程，GET 維持公開；CUD 改為 authenticated。
 * 內部 Feign 通常只做 GET，寫入應由管理端帶 token。
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
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/logging/**").permitAll()
                // 讀取公開
                .requestMatchers(HttpMethod.GET, "/products/**", "/api/products/**").permitAll()
                // 寫入：需已認證（若無全域 JWT filter，外部未帶憑證會 401 — 由 InternalWriteGuardFilter 補強）
                .requestMatchers(HttpMethod.POST, "/products/**", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/products/**", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/products/**", "/api/products/**").permitAll()
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
}
