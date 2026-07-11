package com.microservices.product.config;

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
 * 保護產品寫入 API：驗證 JWT 簽章、期限與 ADMIN 角色。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminWriteGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminWriteGuardFilter.class);
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Value("${security.write-guard.enabled:true}")
    private boolean enabled;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper;

    public AdminWriteGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !isWriteOnProducts(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.warn("Product write rejected: missing Authorization on {} {}",
                    request.getMethod(), request.getRequestURI());
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED", "Missing Authorization header");
            return;
        }

        String role;
        try {
            role = parseClaims(auth.substring(7)).get("role", String.class);
        } catch (Exception ex) {
            log.warn("Product write rejected: invalid/expired token on {} {}",
                    request.getMethod(), request.getRequestURI());
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_TOKEN", "Invalid or expired token");
            return;
        }
        if (!"ADMIN".equalsIgnoreCase(role)) {
            log.warn("Product write rejected: role={} on {} {}",
                    role, request.getMethod(), request.getRequestURI());
            writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                    "ACCESS_DENIED", "ADMIN role required for product mutations");
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
