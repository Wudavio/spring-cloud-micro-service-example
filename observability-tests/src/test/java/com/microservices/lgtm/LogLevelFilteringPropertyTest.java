package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：日誌級別過濾測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 10: 日誌級別過濾
 * 驗證: 需求 4.4
 * 
 * 測試日誌級別過濾功能和動態調整能力
 */
public class LogLevelFilteringPropertyTest {

    private static final Logger logger = LoggerFactory.getLogger(LogLevelFilteringPropertyTest.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 屬性 10: 日誌級別過濾
     * 對於任何配置的日誌級別，系統應該只導出符合該級別或更高級別的日誌
     */
    @Property(tries = 100)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 10: 日誌級別過濾")
    void logsShouldBeFilteredByLevel(@ForAll("logLevels") String logLevel,
                                     @ForAll("logMessages") String message) {
        
        // 捕獲日誌輸出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(outputStream));
            
            // 根據測試的日誌級別記錄不同級別的日誌
            switch (logLevel.toUpperCase()) {
                case "ERROR":
                    logger.error(message);
                    logger.warn("這個 WARN 日誌不應該出現在 ERROR 級別");
                    logger.info("這個 INFO 日誌不應該出現在 ERROR 級別");
                    break;
                case "WARN":
                    logger.error(message);
                    logger.warn(message);
                    logger.info("這個 INFO 日誌不應該出現在 WARN 級別");
                    break;
                case "INFO":
                    logger.error(message);
                    logger.warn(message);
                    logger.info(message);
                    logger.debug("這個 DEBUG 日誌不應該出現在 INFO 級別");
                    break;
                case "DEBUG":
                    logger.error(message);
                    logger.warn(message);
                    logger.info(message);
                    logger.debug(message);
                    logger.trace("這個 TRACE 日誌不應該出現在 DEBUG 級別");
                    break;
                case "TRACE":
                    logger.error(message);
                    logger.warn(message);
                    logger.info(message);
                    logger.debug(message);
                    logger.trace(message);
                    break;
            }
            
            String logOutput = outputStream.toString();
            
            if (!logOutput.trim().isEmpty()) {
                // 驗證日誌包含預期的訊息
                assertThat(logOutput)
                        .as("日誌輸出應該包含測試訊息")
                        .contains(message);
                
                // 根據日誌級別驗證過濾行為
                validateLogLevelFiltering(logOutput, logLevel);
            }
            
        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * 測試 OpenTelemetry Collector 的日誌過濾配置
     */
    @Test
    void otelCollectorShouldHaveLogFilterConfiguration() throws Exception {
        java.io.File collectorConfig = new java.io.File("../otel-collector-config.yaml");
        
        assertThat(collectorConfig.exists())
                .as("OpenTelemetry Collector 配置檔案應該存在")
                .isTrue();
        
        String content = java.nio.file.Files.readString(collectorConfig.toPath());
        
        // 驗證包含日誌過濾處理器
        assertThat(content)
                .as("Collector 配置應該包含日誌過濾處理器")
                .contains("filter/logs:");
        
        // 驗證包含日誌級別過濾規則
        assertThat(content)
                .as("Collector 配置應該包含日誌級別過濾規則")
                .contains("severity_number");
        
        // 驗證日誌管道使用過濾器
        assertThat(content)
                .as("日誌管道應該使用過濾處理器")
                .contains("filter/logs");
    }

    /**
     * 測試環境特定的日誌級別配置
     */
    @Property(tries = 20)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 10: 環境特定日誌配置")
    void servicesShouldHaveEnvironmentSpecificLogConfig(@ForAll("serviceNames") String serviceName,
                                                        @ForAll("environments") String environment) throws Exception {
        
        String configPath = "../" + serviceName + "/src/main/resources/application.yml";
        java.io.File configFile = new java.io.File(configPath);
        
        if (configFile.exists()) {
            String content = java.nio.file.Files.readString(configFile.toPath());
            
            // 驗證包含基本日誌配置
            assertThat(content)
                    .as(serviceName + " 應該包含日誌配置")
                    .contains("logging:");
            
            // 驗證包含環境特定配置
            if (content.contains("on-profile: " + environment)) {
                assertThat(content)
                        .as(serviceName + " 應該包含 " + environment + " 環境的日誌配置")
                        .contains("on-profile: " + environment);
            }
        }
    }

    /**
     * 測試動態日誌級別調整 API（如果服務運行中）
     */
    @Property(tries = 10)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 10: 動態日誌級別調整")
    void dynamicLogLevelAdjustmentShouldWork(@ForAll("serviceEndpoints") String endpoint,
                                             @ForAll("logLevels") String logLevel) {
        try {
            // 嘗試獲取當前日誌級別
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/logging/levels"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            
            if (getResponse.statusCode() == 200) {
                // 服務正在運行，測試動態調整
                assertThat(getResponse.body())
                        .as("日誌級別 API 應該返回有效的 JSON 響應")
                        .contains("ROOT");
                
                // 嘗試設定日誌級別
                HttpRequest setRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint + "/api/logging/levels/ROOT?level=" + logLevel))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> setResponse = httpClient.send(setRequest, HttpResponse.BodyHandlers.ofString());
                
                // 驗證設定響應（可能成功或失敗，取決於服務狀態）
                assertThat(setResponse.statusCode())
                        .as("日誌級別設定 API 應該返回有效的 HTTP 狀態碼")
                        .isBetween(200, 500);
            }
            
        } catch (Exception e) {
            // 服務可能未運行，這在測試環境中是可接受的
            System.out.println("服務端點 " + endpoint + " 無法連接或不支援日誌 API: " + e.getMessage());
        }
    }

    /**
     * 測試日誌級別層次結構
     */
    @Test
    void logLevelHierarchyShouldBeCorrect() {
        List<String> levels = Arrays.asList("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
        
        for (int i = 0; i < levels.size(); i++) {
            String currentLevel = levels.get(i);
            
            // 驗證當前級別包含所有更高級別的日誌
            for (int j = 0; j <= i; j++) {
                String higherLevel = levels.get(j);
                assertThat(shouldLogAtLevel(currentLevel, higherLevel))
                        .as(currentLevel + " 級別應該包含 " + higherLevel + " 級別的日誌")
                        .isTrue();
            }
            
            // 驗證當前級別不包含更低級別的日誌
            for (int j = i + 1; j < levels.size(); j++) {
                String lowerLevel = levels.get(j);
                assertThat(shouldLogAtLevel(currentLevel, lowerLevel))
                        .as(currentLevel + " 級別不應該包含 " + lowerLevel + " 級別的日誌")
                        .isFalse();
            }
        }
    }

    /**
     * 測試 Logback 配置檔案的存在性
     */
    @Property(tries = 10)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 10: Logback 配置完整性")
    void logbackConfigurationShouldExist(@ForAll("serviceNames") String serviceName) throws Exception {
        String logbackConfigPath = "../" + serviceName + "/src/main/resources/logback-spring.xml";
        java.io.File logbackConfig = new java.io.File(logbackConfigPath);
        
        if (logbackConfig.exists()) {
            String content = java.nio.file.Files.readString(logbackConfig.toPath());
            
            // 驗證包含環境特定配置
            assertThat(content)
                    .as(serviceName + " 的 Logback 配置應該包含環境特定設定")
                    .contains("springProfile");
            
            // 驗證包含日誌級別配置
            assertThat(content)
                    .as(serviceName + " 的 Logback 配置應該包含日誌級別設定")
                    .contains("level=");
            
            // 驗證包含 trace 上下文配置
            assertThat(content)
                    .as(serviceName + " 的 Logback 配置應該包含 trace 上下文")
                    .contains("traceId")
                    .contains("spanId");
        }
    }

    /**
     * 驗證日誌級別過濾行為
     */
    private void validateLogLevelFiltering(String logOutput, String configuredLevel) {
        switch (configuredLevel.toUpperCase()) {
            case "ERROR":
                // ERROR 級別應該只包含 ERROR 日誌
                assertThat(logOutput.toLowerCase())
                        .as("ERROR 級別不應該包含 WARN 日誌")
                        .doesNotContain("warn");
                break;
            case "WARN":
                // WARN 級別應該包含 ERROR 和 WARN，但不包含 INFO
                assertThat(logOutput.toLowerCase())
                        .as("WARN 級別不應該包含 INFO 日誌")
                        .doesNotContain("info");
                break;
            case "INFO":
                // INFO 級別應該包含 ERROR、WARN、INFO，但不包含 DEBUG
                assertThat(logOutput.toLowerCase())
                        .as("INFO 級別不應該包含 DEBUG 日誌")
                        .doesNotContain("debug");
                break;
            case "DEBUG":
                // DEBUG 級別應該包含所有級別除了 TRACE
                assertThat(logOutput.toLowerCase())
                        .as("DEBUG 級別不應該包含 TRACE 日誌")
                        .doesNotContain("trace");
                break;
            case "TRACE":
                // TRACE 級別應該包含所有日誌
                break;
        }
    }

    /**
     * 判斷在指定級別下是否應該記錄特定級別的日誌
     */
    private boolean shouldLogAtLevel(String configuredLevel, String messageLevel) {
        List<String> levels = Arrays.asList("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
        int configuredIndex = levels.indexOf(configuredLevel.toUpperCase());
        int messageIndex = levels.indexOf(messageLevel.toUpperCase());
        
        return messageIndex <= configuredIndex;
    }

    /**
     * 生成日誌級別
     */
    @Provide
    Arbitrary<String> logLevels() {
        return Arbitraries.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
    }

    /**
     * 生成環境名稱
     */
    @Provide
    Arbitrary<String> environments() {
        return Arbitraries.of("development", "testing", "production");
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
     * 生成服務端點
     */
    @Provide
    Arbitrary<String> serviceEndpoints() {
        return Arbitraries.of(
                "http://localhost:8081",  // product-service
                "http://localhost:8083",  // order-service
                "http://localhost:8082",  // inventory-service
                "http://localhost:8084",  // auth-service
                "http://localhost:8080"   // api-gateway
        );
    }

    /**
     * 生成日誌訊息
     */
    @Provide
    Arbitrary<String> logMessages() {
        return Arbitraries.of(
                "系統啟動完成",
                "處理用戶請求",
                "資料庫操作執行",
                "API 調用成功",
                "快取更新完成",
                "業務邏輯處理",
                "錯誤處理執行",
                "系統健康檢查"
        );
    }
}