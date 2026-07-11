package com.microservices.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 配置類
 * 配置 Swagger UI 和 API 文檔
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8081}")
    private String serverPort;

    @Bean
    public OpenAPI productServiceOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl("http://localhost:8080/api");
        devServer.setDescription("API Gateway");

        Contact contact = new Contact();
        contact.setEmail("dev@microservices.com");
        contact.setName("微服務開發團隊");

        License mitLicense = new License()
                .name("MIT License")
                .url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("產品服務 API")
                .version("1.0.0")
                .contact(contact)
                .description("微服務訂單庫存系統 - 產品管理服務 API 文檔")
                .license(mitLicense);

        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .servers(List.of(devServer));
    }
}
