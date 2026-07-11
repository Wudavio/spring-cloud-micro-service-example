package com.microservices.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 * 業務 API 由 JwtAuthenticationFilter 強制驗證；此處放行文件與健康檢查。
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
                // cart/orders 由 JwtAuthenticationFilter 驗證，Spring Security 放行以利 filter 注入 userId
                .requestMatchers("/orders/**", "/cart/**").permitAll()
                .requestMatchers("/api/orders/**", "/api/cart/**").permitAll()
                .anyRequest().authenticated()
            );
        
        return http.build();
    }
}
