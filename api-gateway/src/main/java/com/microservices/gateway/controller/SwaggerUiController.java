package com.microservices.gateway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerUiController {

    @GetMapping(value = "/swagger-initializer.js", produces = "application/javascript")
    public String swaggerInitializer() {
        return """
            window.onload = function() {
              window.ui = SwaggerUIBundle({
                configUrl: '/v3/api-docs/swagger-config',
                dom_id: '#swagger-ui',
                deepLinking: true,
                presets: [
                  SwaggerUIBundle.presets.apis,
                  SwaggerUIStandalonePreset
                ],
                plugins: [
                  SwaggerUIBundle.plugins.DownloadUrl
                ],
                layout: "StandaloneLayout",
                validatorUrl: ""
              });
            };
            """;
    }
}