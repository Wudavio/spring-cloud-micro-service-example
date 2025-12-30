package com.microservices.gateway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerTestController {

    @GetMapping(value = "/swagger-test", produces = MediaType.TEXT_HTML_VALUE)
    public String swaggerTest() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Swagger UI Test</title>
                <link rel="stylesheet" type="text/css" href="/webjars/swagger-ui/swagger-ui.css" />
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="/webjars/swagger-ui/swagger-ui-bundle.js"></script>
                <script src="/webjars/swagger-ui/swagger-ui-standalone-preset.js"></script>
                <script>
                window.onload = function() {
                  const ui = SwaggerUIBundle({
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
                    layout: "StandaloneLayout"
                  });
                };
                </script>
            </body>
            </html>
            """;
    }
}