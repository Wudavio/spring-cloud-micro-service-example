package com.microservices.product.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * 保護產品寫入 API：要求 Authorization Bearer，並在 JWT payload 中檢查 role=ADMIN。
 * 不驗證簽章（簽章驗證在 auth-service）；此為閘道後第二道簡易防線，阻擋明顯匿名寫入。
 * 生產環境應改為共用 secret 完整驗簽。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminWriteGuardFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Value("${security.write-guard.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !isWriteOnProducts(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            unauthorized(response, "Missing Authorization header");
            return;
        }

        String role = extractRole(auth.substring(7));
        if (!"ADMIN".equalsIgnoreCase(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"ADMIN role required for product mutations\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWriteOnProducts(HttpServletRequest request) {
        String method = request.getMethod();
        if (!WRITE_METHODS.contains(method)) {
            return false;
        }
        String path = request.getRequestURI();
        return path.startsWith("/products") || path.startsWith("/api/products");
    }

    private String extractRole(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // 簡易解析 "role":"ADMIN"
            int idx = payloadJson.indexOf("\"role\"");
            if (idx < 0) {
                return null;
            }
            int colon = payloadJson.indexOf(':', idx);
            int startQuote = payloadJson.indexOf('"', colon + 1);
            int endQuote = payloadJson.indexOf('"', startQuote + 1);
            if (startQuote < 0 || endQuote < 0) {
                return null;
            }
            return payloadJson.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            return null;
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
