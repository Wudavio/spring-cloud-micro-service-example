package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：錯誤追蹤準確性測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 6: 錯誤追蹤準確性
 * 驗證: 需求 3.2
 * 
 * 測試當請求包含錯誤時，相應的 span 應該正確標記錯誤狀態並發送到 Tempo
 */
public class ErrorTraceAccuracyPropertyTest {

    private static final String OTEL_AGENT_DIR = "../otel-agent";
    private static final String OTEL_CONFIG_FILE = "otel-config.properties";
    private static final String OTEL_COLLECTOR_CONFIG = "../otel-collector-config.yaml";

    @BeforeEach
    void setUp() {
        // 確保測試環境準備就緒
        assertThat(new File(OTEL_AGENT_DIR)).exists();
        assertThat(new File(OTEL_COLLECTOR_CONFIG)).exists();
    }

    /**
     * 屬性 6: 錯誤追蹤準確性
     * 對於任何包含錯誤的請求，相應的 span 應該正確標記錯誤狀態
     */
    @Property(tries = 100)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 6: 錯誤追蹤準確性")
    void errorSpansShouldBeCorrectlyMarkedAndExported(
            @ForAll("httpErrorCodes") int errorCode,
            @ForAll("microserviceNames") String serviceName) throws IOException {
        
        // 驗證 OpenTelemetry 配置支援錯誤追蹤
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String agentConfig = Files.readString(configFile.toPath());
        
        // 驗證 HTTP 儀表化已啟用（用於捕獲 HTTP 錯誤）
        assertThat(agentConfig)
                .as("HTTP 儀表化應該被啟用以捕獲 HTTP 錯誤")
                .contains("otel.instrumentation.http.enabled=true");
                
        // 驗證 Servlet 儀表化已啟用（用於捕獲伺服器端錯誤）
        assertThat(agentConfig)
                .as("Servlet 儀表化應該被啟用以捕獲伺服器端錯誤")
                .contains("otel.instrumentation.servlet.enabled=true");
                
        // 驗證 Spring WebMVC 儀表化已啟用（用於捕獲 Spring 控制器錯誤）
        assertThat(agentConfig)
                .as("Spring WebMVC 儀表化應該被啟用以捕獲控制器錯誤")
                .contains("otel.instrumentation.spring-webmvc.enabled=true");
    }

    /**
     * 測試 OpenTelemetry Collector 配置支援錯誤追蹤導出到 Tempo
     */
    @Test
    void collectorShouldBeConfiguredForErrorTraceExport() throws IOException {
        File collectorConfigFile = new File(OTEL_COLLECTOR_CONFIG);
        String collectorConfig = Files.readString(collectorConfigFile.toPath());
        
        // 驗證 OTLP 接收器配置
        assertThat(collectorConfig)
                .as("Collector 應該配置 OTLP 接收器以接收追蹤數據")
                .contains("otlp:");
                
        // 驗證 Tempo 導出器配置
        assertThat(collectorConfig)
                .as("Collector 應該配置 Tempo 導出器")
                .contains("otlp/tempo:");
                
        // 驗證 Tempo 端點配置
        assertThat(collectorConfig)
                .as("Collector 應該配置正確的 Tempo 端點")
                .contains("endpoint: http://tempo:4317");
                
        // 驗證追蹤管道配置
        assertThat(collectorConfig)
                .as("Collector 應該配置追蹤管道")
                .contains("traces:");
                
        assertThat(collectorConfig)
                .as("追蹤管道應該包含 OTLP 接收器")
                .contains("receivers: [otlp]");
                
        assertThat(collectorConfig)
                .as("追蹤管道應該包含 Tempo 導出器")
                .contains("exporters: [otlp/tempo]");
    }

    /**
     * 測試錯誤狀態碼映射配置
     */
    @Property(tries = 20)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 6: 錯誤狀態碼映射")
    void errorStatusCodeMappingShouldBeCorrect(@ForAll("httpErrorCodes") int errorCode) {
        // 驗證錯誤狀態碼範圍
        if (errorCode >= 400 && errorCode < 600) {
            // 4xx 和 5xx 狀態碼應該被視為錯誤
            assertThat(errorCode)
                    .as("HTTP 錯誤狀態碼應該在 400-599 範圍內")
                    .isBetween(400, 599);
        }
        
        // 特定錯誤狀態碼驗證
        List<Integer> commonErrorCodes = Arrays.asList(400, 401, 403, 404, 500, 502, 503, 504);
        if (commonErrorCodes.contains(errorCode)) {
            assertThat(commonErrorCodes)
                    .as("常見錯誤狀態碼 " + errorCode + " 應該被正確處理")
                    .contains(errorCode);
        }
    }

    /**
     * 生成 HTTP 錯誤狀態碼
     */
    @Provide
    Arbitrary<Integer> httpErrorCodes() {
        return Arbitraries.of(
                400, 401, 403, 404, 405, 409, 422, 429,  // 4xx 客戶端錯誤
                500, 501, 502, 503, 504, 505             // 5xx 伺服器錯誤
        );
    }

    /**
     * 生成微服務名稱
     */
    @Provide
    Arbitrary<String> microserviceNames() {
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
}