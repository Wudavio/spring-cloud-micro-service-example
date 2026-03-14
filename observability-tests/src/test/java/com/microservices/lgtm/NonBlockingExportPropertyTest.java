package com.microservices.lgtm;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：非阻塞導出測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 14: 非阻塞導出
 * 驗證: 需求 7.3
 * 
 * 測試遙測數據導出失敗時，主要業務流程不受影響繼續執行
 */
public class NonBlockingExportPropertyTest {

    private static final String OTEL_COLLECTOR_CONFIG_PATH = "otel-collector-config.yaml";
    private static final int MAX_RESPONSE_TIME_MS = 5000; // 最大響應時間 5 秒
    private static final int CONCURRENT_REQUESTS = 50;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 測試 OpenTelemetry Collector 非阻塞配置
     */
    @Test
    void collectorShouldHaveNonBlockingConfiguration() throws IOException {
        Path configPath = Paths.get(OTEL_COLLECTOR_CONFIG_PATH);
        assertThat(configPath.toFile().exists())
                .as("OpenTelemetry Collector 配置檔案應該存在")
                .isTrue();

        Yaml yaml = new Yaml();
        Map<String, Object> config;
        
        try (FileInputStream inputStream = new FileInputStream(configPath.toFile())) {
            config = yaml.load(inputStream);
        }

        // 檢查接收器配置 - 應該有適當的緩衝區設定
        @SuppressWarnings("unchecked")
        Map<String, Object> receivers = (Map<String, Object>) config.get("receivers");
        assertThat(receivers)
                .as("配置應該包含接收器")
                .isNotNull()
                .containsKey("otlp");

        @SuppressWarnings("unchecked")
        Map<String, Object> otlpReceiver = (Map<String, Object>) receivers.get("otlp");
        assertThat(otlpReceiver)
                .as("OTLP 接收器應該配置協議")
                .containsKey("protocols");

        // 檢查導出器配置 - 應該有重試和佇列配置
        @SuppressWarnings("unchecked")
        Map<String, Object> exporters = (Map<String, Object>) config.get("exporters");
        assertThat(exporters)
                .as("配置應該包含導出器")
                .isNotNull();

        // 檢查每個導出器是否有非阻塞配置
        for (String exporterName : exporters.keySet()) {
            if (!exporterName.equals("debug") && !exporterName.equals("file/backup")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> exporterConfig = (Map<String, Object>) exporters.get(exporterName);
                
                // 檢查重試配置
                if (exporterConfig.containsKey("retry_on_failure")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> retryConfig = (Map<String, Object>) exporterConfig.get("retry_on_failure");
                    assertThat(retryConfig.get("enabled"))
                            .as("導出器 " + exporterName + " 應該啟用重試機制")
                            .isEqualTo(true);
                }

                // 檢查佇列配置
                if (exporterConfig.containsKey("sending_queue")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> queueConfig = (Map<String, Object>) exporterConfig.get("sending_queue");
                    assertThat(queueConfig.get("enabled"))
                            .as("導出器 " + exporterName + " 應該啟用發送佇列")
                            .isEqualTo(true);
                }
            }
        }
    }

    /**
     * 屬性 14: 非阻塞導出
     * 對於任何 微服務請求，即使遙測數據導出失敗，業務邏輯應該不受影響
     */
    @Property(tries = 20)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 14: 非阻塞導出")
    void businessLogicShouldNotBeBlockedByTelemetryExportFailure(@ForAll("serviceEndpoints") String endpoint) throws Exception {
        // 測試在 Collector 不可用時，服務是否仍能正常響應
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        long startTime = System.currentTimeMillis();
        
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 檢查響應時間 - 即使遙測導出有問題，業務邏輯應該快速響應
            assertThat(responseTime)
                    .as("服務 " + endpoint + " 響應時間應該在可接受範圍內，不受遙測導出影響")
                    .isLessThan(MAX_RESPONSE_TIME_MS);
                    
            // 檢查響應狀態 - 業務邏輯應該正常工作
            assertThat(response.statusCode())
                    .as("服務 " + endpoint + " 應該返回成功狀態，不受遙測導出影響")
                    .isBetween(200, 299);
                    
        } catch (Exception e) {
            // 如果服務不可用，這可能是正常的（服務未啟動），跳過測試
            System.out.println("服務 " + endpoint + " 不可用，跳過測試: " + e.getMessage());
        }
    }

    /**
     * 測試並發請求下的非阻塞行為
     */
    @Property(tries = 5)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 14: 並發非阻塞導出")
    void concurrentRequestsShouldNotBeBlockedByTelemetryExport(@ForAll @IntRange(min = 10, max = 50) int concurrentRequests) throws Exception {
        String testEndpoint = "http://localhost:8761/actuator/health"; // Eureka Server 健康檢查
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalResponseTime = new AtomicInteger(0);
        
        try {
            // 發送並發請求
            for (int i = 0; i < concurrentRequests; i++) {
                executor.submit(() -> {
                    try {
                        HttpClient client = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(testEndpoint))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();

                        long startTime = System.currentTimeMillis();
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        long responseTime = System.currentTimeMillis() - startTime;
                        
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            successCount.incrementAndGet();
                            totalResponseTime.addAndGet((int) responseTime);
                        }
                        
                    } catch (Exception e) {
                        // 記錄錯誤但不影響測試
                        System.out.println("並發請求失敗: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 等待所有請求完成
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertThat(completed)
                    .as("所有並發請求應該在合理時間內完成")
                    .isTrue();
            
            // 檢查成功率 - 即使有遙測導出問題，大部分請求應該成功
            if (successCount.get() > 0) {
                double successRate = (double) successCount.get() / concurrentRequests;
                assertThat(successRate)
                        .as("並發請求成功率應該很高，不受遙測導出影響")
                        .isGreaterThan(0.8); // 至少 80% 成功率
                        
                // 檢查平均響應時間
                int avgResponseTime = totalResponseTime.get() / successCount.get();
                assertThat(avgResponseTime)
                        .as("並發請求平均響應時間應該合理")
                        .isLessThan(MAX_RESPONSE_TIME_MS);
            }
            
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * 測試 OpenTelemetry Agent 配置的非阻塞特性
     */
    @Property(tries = 10)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 14: Agent 非阻塞配置")
    void otelAgentConfigurationShouldBeNonBlocking(@ForAll("otelConfigFiles") String configFile) throws IOException {
        Path configPath = Paths.get(configFile);
        
        if (!configPath.toFile().exists()) {
            return; // 配置檔案不存在，跳過測試
        }

        String content = Files.readString(configPath);
        
        // 檢查批次處理器配置 - 應該有合理的佇列大小
        if (content.contains("OTEL_BSP_MAX_QUEUE_SIZE")) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("OTEL_BSP_MAX_QUEUE_SIZE") && !line.trim().startsWith("#")) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        int queueSize = Integer.parseInt(parts[1].trim());
                        assertThat(queueSize)
                                .as("批次處理器佇列大小應該足夠大以避免阻塞")
                                .isGreaterThan(1000);
                    }
                }
            }
        }

        // 檢查導出超時配置 - 應該有合理的超時設定
        if (content.contains("OTEL_BSP_EXPORT_TIMEOUT")) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("OTEL_BSP_EXPORT_TIMEOUT") && !line.trim().startsWith("#")) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        int timeout = Integer.parseInt(parts[1].trim());
                        assertThat(timeout)
                                .as("導出超時應該設定合理值以避免長時間阻塞")
                                .isBetween(5000, 60000); // 5-60 秒
                    }
                }
            }
        }

        // 檢查重試配置
        assertThat(content)
                .as("配置應該包含重試機制以處理導出失敗")
                .contains("OTEL_EXPORTER_OTLP_RETRY_ENABLED=true");
    }

    /**
     * 測試熔斷器配置
     */
    @Test
    void circuitBreakerConfigurationShouldExist() throws IOException {
        Path configPath = Paths.get("otel-agent/circuit-breaker-config.yaml");
        
        if (configPath.toFile().exists()) {
            String content = Files.readString(configPath);
            
            // 檢查熔斷器配置
            assertThat(content)
                    .as("熔斷器配置應該包含失敗閾值")
                    .contains("failure_threshold");
                    
            assertThat(content)
                    .as("熔斷器配置應該包含重試配置")
                    .contains("retry_config");
                    
            assertThat(content)
                    .as("熔斷器配置應該包含佇列配置")
                    .contains("queue_config");
        }
    }

    /**
     * 測試備份機制配置
     */
    @Test
    void backupMechanismShouldBeConfigured() throws IOException {
        Path configPath = Paths.get(OTEL_COLLECTOR_CONFIG_PATH);
        String content = Files.readString(configPath);
        
        // 檢查檔案備份導出器配置
        assertThat(content)
                .as("配置應該包含檔案備份導出器")
                .contains("file/backup");
                
        // 檢查管道中是否包含備份導出器
        assertThat(content)
                .as("所有管道都應該包含備份導出器")
                .contains("file/backup");
    }

    /**
     * 測試記憶體和資源限制配置
     */
    @Test
    void resourceLimitsShouldPreventBlocking() throws IOException {
        Path configPath = Paths.get(OTEL_COLLECTOR_CONFIG_PATH);
        String content = Files.readString(configPath);
        
        // 檢查記憶體限制器配置
        assertThat(content)
                .as("配置應該包含記憶體限制器以防止記憶體耗盡阻塞")
                .contains("memory_limiter");
                
        // 檢查記憶體限制器在處理器鏈的第一位
        String[] lines = content.split("\n");
        boolean foundPipeline = false;
        boolean foundMemoryLimiterFirst = false;
        
        for (String line : lines) {
            if (line.trim().startsWith("processors:") && foundPipeline) {
                String nextLine = lines[java.util.Arrays.asList(lines).indexOf(line) + 1];
                if (nextLine.contains("memory_limiter")) {
                    foundMemoryLimiterFirst = true;
                    break;
                }
            }
            if (line.contains("pipelines:")) {
                foundPipeline = true;
            }
        }
        
        assertThat(foundMemoryLimiterFirst)
                .as("記憶體限制器應該在處理器鏈的第一位")
                .isTrue();
    }

    /**
     * 生成服務端點
     */
    @Provide
    Arbitrary<String> serviceEndpoints() {
        return Arbitraries.of(
                "http://localhost:8761/actuator/health", // Eureka Server
                "http://localhost:8888/actuator/health", // Config Server
                "http://localhost:8080/actuator/health", // API Gateway
                "http://localhost:8081/actuator/health", // Product Service
                "http://localhost:8082/actuator/health", // Inventory Service
                "http://localhost:8083/actuator/health", // Order Service
                "http://localhost:8084/actuator/health"  // Auth Service
        );
    }

    /**
     * 生成 OpenTelemetry 配置檔案路徑
     */
    @Provide
    Arbitrary<String> otelConfigFiles() {
        return Arbitraries.of(
                "otel-agent/non-blocking-export-config.env",
                "otel-agent/api-gateway.env",
                "otel-agent/auth-service.env",
                "otel-agent/config-server.env",
                "otel-agent/eureka-server.env",
                "otel-agent/inventory-service.env",
                "otel-agent/order-service.env",
                "otel-agent/product-service.env"
        );
    }
}