package com.microservices.product.config;

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

class AdminWriteGuardFilterTest {

    private static final String SECRET = "testSecretKeyForJwtSigningThatIsAtLeast32BytesLong";
    private AdminWriteGuardFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AdminWriteGuardFilter(new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
    }

    @Test
    void rejectsForgedAdminToken() throws Exception {
        MockHttpServletResponse response = invoke("eyJhbGciOiJub25lIn0.eyJyb2xlIjoiQURNSU4ifQ.");
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
    }

    @Test
    void acceptsSignedUnexpiredAdminToken() throws Exception {
        String token = Jwts.builder().claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        assertThat(invoke(token).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse invoke(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/products");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
