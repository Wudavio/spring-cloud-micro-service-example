package com.microservices.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryWriteGuardFilterTest {

    private static final String SECRET = "testSecretKeyForJwtSigningThatIsAtLeast32BytesLong";
    private InventoryWriteGuardFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InventoryWriteGuardFilter(new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
    }

    @Test
    void rejectsCustomerForStockManagement() throws Exception {
        assertThat(invoke("PUT", "/inventory/1/stock", signed("CUSTOMER")).getStatus()).isEqualTo(403);
    }

    @Test
    void acceptsAdminForStockManagement() throws Exception {
        assertThat(invoke("PUT", "/inventory/1/stock", signed("ADMIN")).getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsCustomerForCreateInventory() throws Exception {
        assertThat(invoke("POST", "/inventory/42", signed("CUSTOMER")).getStatus()).isEqualTo(403);
    }

    @Test
    void acceptsSignedCustomerForReserve() throws Exception {
        assertThat(invoke("POST", "/inventory/42/reserve", signed("CUSTOMER")).getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsForgedToken() throws Exception {
        assertThat(invoke("PUT", "/inventory/1/stock", "eyJhbGciOiJub25lIn0.eyJyb2xlIjoiQURNSU4ifQ.")
                .getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredAdminToken() throws Exception {
        String expired = Jwts.builder().claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        MockHttpServletResponse response = invoke("PUT", "/inventory/1/stock", expired);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
    }

    private String signed(String role) {
        return Jwts.builder().claim("role", role)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private MockHttpServletResponse invoke(String method, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
