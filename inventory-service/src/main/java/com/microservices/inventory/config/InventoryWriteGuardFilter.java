package com.microservices.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.api.ApiErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
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

    private static final Logger log = LoggerFactory.getLogger(InventoryWriteGuardFilter.class);
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Value("${security.write-guard.enabled:true}")
    private boolean enabled;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper;

    public InventoryWriteGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
            log.warn("Inventory write rejected: missing Authorization on {} {}", method, path);
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED", "Missing Authorization header");
            return;
        }

        Claims claims;
        try {
            claims = parseClaims(auth.substring(7));
        } catch (Exception ex) {
            log.warn("Inventory write rejected: invalid/expired token on {} {}", method, path);
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_TOKEN", "Invalid or expired token");
            return;
        }

        if (requiresAdmin(path, method)) {
            String role = claims.get("role", String.class);
            if (!"ADMIN".equalsIgnoreCase(role)) {
                log.warn("Inventory write rejected: role={} on {} {}", role, method, path);
                writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                        "ACCESS_DENIED", "ADMIN role required for stock management");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInventoryPath(String path) {
        return path.startsWith("/inventory") || path.startsWith("/api/inventory");
    }

    /**
     * stock 調整、建立庫存、清理過期預留僅 ADMIN。
     * POST /inventory/{productId}（無後續子路徑）視為建立庫存。
     */
    private boolean requiresAdmin(String path, String method) {
        if (path.contains("/stock") || path.contains("/cleanup-expired")) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        String normalized = path.startsWith("/api/") ? path.substring(4) : path;
        return normalized.matches("/inventory/\\d+/?");
    }

    private Claims parseClaims(String jwt) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(jwt)
                .getBody();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(status, code, message,
                request.getRequestURI(), org.slf4j.MDC.get("traceId"), Map.of()));
    }
}
