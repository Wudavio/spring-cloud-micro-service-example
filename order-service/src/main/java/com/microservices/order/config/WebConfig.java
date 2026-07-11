package com.microservices.order.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 配置 — JWT 過濾器需匹配 Gateway StripPrefix 後的實際路徑
 * Gateway: /api/cart/**、/api/orders/** → 服務端 /cart/**、/orders/**
 */
@Configuration
public class WebConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter() {
        FilterRegistrationBean<JwtAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(jwtAuthenticationFilter);
        // Gateway strip 後路徑 + 直連 /api 路徑皆覆蓋
        registrationBean.addUrlPatterns(
                "/cart", "/cart/*",
                "/orders", "/orders/*",
                "/api/cart", "/api/cart/*",
                "/api/orders", "/api/orders/*"
        );
        registrationBean.setOrder(1);
        return registrationBean;
    }
}
