package com.microservices.order.config;

import com.microservices.order.client.AuthServiceClient;
import feign.FeignException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 認證過濾器
 * 從 Authorization header 中提取 JWT token，驗證並獲取用戶ID
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    @Autowired
    private AuthServiceClient authServiceClient;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        // 跳過不需要認證的端點
        String requestPath = request.getRequestURI();
        if (shouldSkipAuthentication(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                // 驗證 token 並獲取用戶ID
                Long userId = authServiceClient.getUserId(authHeader);
                
                // 將用戶ID設置到請求屬性中
                request.setAttribute("userId", userId);
                
                logger.debug("成功驗證用戶: userId={}", userId);
                
            } catch (FeignException e) {
                logger.error("Token 驗證失敗: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
                return;
            } catch (Exception e) {
                logger.error("Token 驗證異常: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Authentication failed\"}");
                return;
            }
        } else {
            logger.debug("缺少 Authorization header: path={}", requestPath);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing Authorization header\"}");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 判斷是否應該跳過認證
     */
    private boolean shouldSkipAuthentication(String requestPath) {
        return requestPath.startsWith("/actuator/") ||
               requestPath.startsWith("/swagger-ui/") ||
               requestPath.startsWith("/v3/api-docs") ||
               requestPath.equals("/swagger-ui.html") ||
               requestPath.startsWith("/api/logging/");
    }
}
