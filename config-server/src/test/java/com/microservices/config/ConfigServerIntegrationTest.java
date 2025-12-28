package com.microservices.config;

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
    "spring.cloud.config.server.git.uri=classpath:/config-repo"
})
class ConfigServerIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void shouldStartConfigServer() {
        // Test that Config server starts successfully
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldServeApplicationConfiguration() {
        // Test that application configuration is served
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/application/default", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("eureka");
    }

    @Test
    void shouldServeServiceSpecificConfiguration() {
        // Test that service-specific configuration is served
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/product-service/default", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("product-service");
    }

    @Test
    void shouldServeApiGatewayConfiguration() {
        // Test that API Gateway configuration is served
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api-gateway/default", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("gateway");
    }

    @Test
    void shouldServeInventoryServiceConfiguration() {
        // Test that Inventory service configuration is served
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/inventory-service/default", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("inventory-service");
    }

    @Test
    void shouldServeOrderServiceConfiguration() {
        // Test that Order service configuration is served
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/order-service/default", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("order-service");
    }
}