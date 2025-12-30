package com.microservices.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class SwaggerController {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public SwaggerController(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping("/v3/api-docs/{service}")
    public Mono<ResponseEntity<String>> getServiceApiDocs(@PathVariable("service") String service) {
        String serviceUrl = getServiceUrl(service);
        
        if (serviceUrl == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return webClientBuilder.build()
                .get()
                .uri(serviceUrl + "/v3/api-docs")
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    // 修改 OpenAPI 文檔中的 servers 配置，指向 API Gateway
                    String modifiedBody = modifyOpenApiServers(body, service);
                    return ResponseEntity.ok(modifiedBody);
                })
                .onErrorReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"error\":\"Service " + service + " is not available\"}"));
    }

    private String getServiceUrl(String service) {
        // 在 Docker 環境中使用服務名稱直接連接，注意端口映射
        Map<String, String> serviceUrls = Map.of(
                "auth-service", "http://auth-service:8084",
                "product-service", "http://product-service:8081", 
                "inventory-service", "http://inventory-service:8082",
                "order-service", "http://order-service:8083"
        );
        
        return serviceUrls.get(service);
    }

    private String modifyOpenApiServers(String openApiJson, String service) {
        try {
            JsonNode rootNode = objectMapper.readTree(openApiJson);
            ObjectNode objectNode = (ObjectNode) rootNode;
            
            // 創建新的 servers 配置
            ArrayNode serversArray = objectMapper.createArrayNode();
            ObjectNode serverNode = objectMapper.createObjectNode();
            serverNode.put("url", "http://localhost:8080" + getServicePrefix(service));
            serverNode.put("description", "API Gateway - " + getServiceDisplayName(service));
            serversArray.add(serverNode);
            
            // 替換 servers 配置
            objectNode.set("servers", serversArray);
            
            return objectMapper.writeValueAsString(objectNode);
        } catch (Exception e) {
            // 如果 JSON 處理失敗，返回原始內容
            return openApiJson;
        }
    }

    private String getServicePrefix(String service) {
        // 由於各服務的 OpenAPI 文檔中已經包含了完整的路徑（如 /api/products），
        // 這裡只需要返回 Gateway 的基礎 URL，不需要額外的路徑前綴
        return "";
    }

    private String getServiceDisplayName(String service) {
        Map<String, String> serviceNames = Map.of(
                "auth-service", "認證服務",
                "product-service", "商品服務",
                "inventory-service", "庫存服務",
                "order-service", "訂單服務"
        );
        
        return serviceNames.getOrDefault(service, service);
    }
}