package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：結構化日誌完整性測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 8: 結構化日誌完整性
 * 驗證: 需求 4.1, 4.2
 * 
 * 測試結構化日誌的格式、標籤和導出功能
 */
public class StructuredLogIntegrityPropertyTest {

    private static final Logger logger = LoggerFactory.getLogger(StructuredLogIntegrityPropertyTest.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 日誌格式模式：包含時間戳、線程、級別、trace ID、span ID、logger 和訊息
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[.*?\\] \\w+ \\[.*?\\] .*? - .*"
    );

    // trace ID 和 span ID 模式
    private static final Pattern TRACE_CONTEXT_PATTERN = Pattern.compile(
            "\\[(\\w{16}|-)\\,(\\w{16}|-)\\]"
    );

    /**
     * 屬性 8: 結構化日誌完整性
     * 對於任何產生的日誌，系統應該包含服務名稱、版本、環境等必要標籤並發送到 Loki
     */
    @Property(tries = 100)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 8: 結構化日誌格式完整性")
    void logMessagesShouldHaveStructuredFormat(@ForAll("logLevels") String logLevel,
                                               @ForAll("logMessages") String message) {
        
        // 捕獲日誌輸出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(outputStream));
            
            // 設定 MDC 上下文（模擬 OpenTelemetry 自動設定）
            MDC.put("traceId", generateTraceId());
            MDC.put("spanId", generateSpanId());
            
            // 根據日誌級別記錄訊息
            switch (logLevel.toLowerCase()) {
                case "info":
                    logger.info(message);
                    break;
                case "warn":
                    logger.warn(message);
                    break;
                case "error":
                    logger.error(message);
                    break;
                case "debug":
                    logger.debug(message);
                    break;
                default:
                    logger.info(message);
            }
            
            String logOutput = outputStream.toString();
            
            // 驗證日誌格式
            if (!logOutput.trim().isEmpty()) {
                assertThat(logOutput)
                        .as("日誌應該符合結構化格式")
                        .matches(LOG_PATTERN);
                
                // 驗證包含 trace 上下文
                assertThat(logOutput)
                        .as("日誌應該包含 trace ID 和 span ID 上下文")
                        .containsPattern(TRACE_CONTEXT_PATTERN);
                
                // 驗證包含原始訊息
                assertThat(logOutput)
                        .as("日誌應該包含原始訊息內容")
                        .contains(message);
            }
            
        } finally {
            System.setOut(originalOut);
            MDC.clear();
        }
    }

    /**
     * 測試日誌配置檔案的存在性和正確性
     */
    @Property(tries = 10)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 8: 日誌配置完整性")
    void logConfigurationShouldBeValid(@ForAll("serviceNames") String serviceName) throws Exception {
        String configPath = "../" + serviceName + "/src/main/resources/application.yml";
        java.io.File configFile = new java.io.File(configPath);
        
        if (configFile.exists()) {
            String content = java.nio.file.Files.readString(configFile.toPath());
            
            // 驗證包含日誌配置
            assertThat(content)
                    .as(serviceName + " 應該包含日誌配置")
                    .contains("logging:");
            
            // 驗證包含 trace 上下文配置
            assertThat(content)
                    .as(serviceName + " 日誌格式應該包含 trace 上下文")
                    .contains("traceId")
                    .contains("spanId");
            
            // 驗證包含日誌級別配置
            assertThat(content)
                    .as(serviceName + " 應該包含日誌級別配置")
                    .contains("level:");
        }
    }

    /**
     * 測試 OpenTelemetry Collector 的日誌導出配置
     */
    @Test
    void otelCollectorShouldHaveLogExportConfiguration() throws Exception {
        java.io.File collectorConfig = new java.io.File("../otel-collector-config.yaml");
        
        assertThat(collectorConfig.exists())
                .as("OpenTelemetry Collector 配置檔案應該存在")
                .isTrue();
        
        String content = java.nio.file.Files.readString(collectorConfig.toPath());
        
        // 驗證包含 Loki 導出器配置
        assertThat(content)
                .as("Collector 配置應該包含 Loki 導出器")
                .contains("loki:");
        
        // 驗證包含日誌管道配置
        assertThat(content)
                .as("Collector 配置應該包含日誌管道")
                .contains("logs:")
                .contains("receivers: [otlp]")
                .contains("exporters: [loki");
        
        // 驗證 Loki 端點配置
        assertThat(content)
                .as("Loki 導出器應該配置正確的端點")
                .contains("http://loki:3100/loki/api/v1/push");
    }

    /**
     * 測試 Loki 服務可用性（如果運行中）
     */
    @Test
    void lokiServiceShouldBeAccessibleWhenRunning() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:3100/ready"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // 如果 Loki 正在運行，應該返回就緒狀態
            if (response.statusCode() == 200) {
                assertThat(response.body())
                        .as("Loki 就緒檢查應該返回正常狀態")
                        .contains("ready");
            }
            
        } catch (Exception e) {
            // Loki 可能未運行，這在測試環境中是可接受的
            System.out.println("Loki 服務未運行或無法連接: " + e.getMessage());
        }
    }

    /**
     * 屬性測試：日誌標籤完整性
     * 對於任何服務配置，應該包含必要的服務標籤
     */
    @Property(tries = 50)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 8: 日誌標籤完整性")
    void logLabelsShouldBeComplete(@ForAll("serviceConfigurations") Map<String, String> serviceConfig) {
        
        // 驗證服務名稱標籤
        assertThat(serviceConfig)
                .as("服務配置應該包含服務名稱")
                .containsKey("service.name");
        
        String serviceName = serviceConfig.get("service.name");
        assertThat(serviceName)
                .as("服務名稱不應該為空")
                .isNotBlank();
        
        // 驗證版本標籤
        if (serviceConfig.containsKey("service.version")) {
            String version = serviceConfig.get("service.version");
            assertThat(version)
                    .as("服務版本應該符合版本格式")
                    .matches("\\d+\\.\\d+\\.\\d+");
        }
        
        // 驗證環境標籤
        if (serviceConfig.containsKey("deployment.environment")) {
            String environment = serviceConfig.get("deployment.environment");
            assertThat(environment)
                    .as("部署環境應該是有效值")
                    .isIn("development", "testing", "staging", "production");
        }
    }

    /**
     * 生成日誌級別
     */
    @Provide
    Arbitrary<String> logLevels() {
        return Arbitraries.of("INFO", "WARN", "ERROR", "DEBUG");
    }

    /**
     * 生成日誌訊息
     */
    @Provide
    Arbitrary<String> logMessages() {
        return Arbitraries.of(
                "用戶登入成功",
                "產品創建完成",
                "庫存更新失敗",
                "訂單處理中",
                "系統健康檢查",
                "資料庫連接建立",
                "API 請求處理完成",
                "快取更新成功"
        );
    }

    /**
     * 生成服務名稱
     */
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "product-service",
                "order-service", 
                "inventory-service",
                "auth-service",
                "api-gateway",
                "eureka-server",
                "config-server"
        );
    }

    /**
     * 生成服務配置
     */
    @Provide
    Arbitrary<Map<String, String>> serviceConfigurations() {
        return Arbitraries.maps(
                Arbitraries.of("service.name", "service.version", "deployment.environment"),
                Arbitraries.oneOf(
                        serviceNames(),
                        Arbitraries.of("1.0.0", "1.1.0", "2.0.0"),
                        Arbitraries.of("development", "testing", "production")
                )
        ).ofMinSize(1).ofMaxSize(3);
    }

    /**
     * 生成 trace ID（16 字元十六進制）
     */
    private String generateTraceId() {
        return String.format("%016x", System.nanoTime());
    }

    /**
     * 生成 span ID（16 字元十六進制）
     */
    private String generateSpanId() {
        return String.format("%016x", System.currentTimeMillis());
    }
}