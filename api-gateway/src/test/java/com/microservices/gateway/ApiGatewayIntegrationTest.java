package com.microservices.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.cloud.gateway.routes[0].id=test-route",
    "spring.cloud.gateway.routes[0].uri=http://httpbin.org",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/test/**"
})
class ApiGatewayIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void shouldStartApiGateway() {
        // Test that API Gateway starts successfully
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldExposeGatewayRoutes() {
        // Test that Gateway routes endpoint is accessible
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/gateway/routes", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldHandleUnknownRoutes() {
        // Test that unknown routes return 404
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/unknown-path", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldProvideActuatorEndpoints() {
        // Test that actuator endpoints are available
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("health");
    }
}