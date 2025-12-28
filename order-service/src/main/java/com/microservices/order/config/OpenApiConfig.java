package com.microservices.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 配置
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${server.port:8083}")
    private String serverPort;
    
    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("訂單服務 API")
                .description("微服務訂單庫存系統 - 訂單服務 API 文檔")
                .version("1.0.0")
                .contact(new Contact()
                    .name("開發團隊")
                    .email("dev@microservices.com")
                    .url("https://github.com/microservices/order-inventory-system"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("本地開發環境"),
                new Server()
                    .url("http://localhost:8080")
                    .description("API Gateway")
            ))
            .tags(List.of(
                new Tag()
                    .name("購物車管理")
                    .description("購物車相關的 API 操作"),
                new Tag()
                    .name("訂單管理")
                    .description("訂單相關的 API 操作")
            ));
    }
}