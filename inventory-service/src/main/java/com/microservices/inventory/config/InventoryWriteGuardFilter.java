package com.microservices.inventory.config;

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
 * 庫存寫入防護：
 * - stock 更新 / cleanup：需 ADMIN
 * - reserve / release / confirm：需帶 Bearer（訂單服務會轉發使用者 JWT）
 * GET 查詢維持公開
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InventoryWriteGuardFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Value("${security.write-guard.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!isInventoryPath(path) || "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!MUTATING.contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            unauthorized(response, "Missing Authorization header");
            return;
        }

        boolean adminOnly = path.contains("/stock") || path.contains("/cleanup-expired");
        if (adminOnly) {
            String role = extractRole(auth.substring(7));
            if (!"ADMIN".equalsIgnoreCase(role)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"ADMIN role required for stock management\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInventoryPath(String path) {
        return path.startsWith("/inventory") || path.startsWith("/api/inventory");
    }

    private String extractRole(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
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
