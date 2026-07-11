package com.microservices.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置。
 * 業務寫入由 {@link InventoryWriteGuardFilter} 驗證 JWT；此處限制 Actuator 暴露面。
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
                // 業務 API 由 InventoryWriteGuardFilter 補強；Spring Security 放行以利 filter 處理
                .requestMatchers("/inventory/**").permitAll()
                .requestMatchers("/api/inventory/**").permitAll()
                .anyRequest().authenticated()
            );
        
        return http.build();
    }
}