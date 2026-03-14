package com.microservices.lgtm;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：批次處理效率測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 4: 批次處理效率
 * 驗證: 需求 2.3
 * 
 * 測試 OpenTelemetry Collector 和微服務的批次處理配置是否符合效率要求
 */
public class BatchProcessingEfficiencyPropertyTest {

    private static final String OTEL_COLLECTOR_CONFIG_PATH = "../otel-collector-config.yaml";
    private static final int MIN_BATCH_SIZE = 100;
    private static final int MAX_BATCH_TIMEOUT_MS = 60000;
    private static final int MIN_EXPORT_INTERVAL_MS = 5000;

    /**
     * 測試 OpenTelemetry Collector 批次處理配置
     */
    @Test
    void collectorBatchConfigurationShouldBeEfficient() throws IOException {
        Path configPath = Paths.get(OTEL_COLLECTOR_CONFIG_PATH);
        assertThat(configPath.toFile().exists())
                .as("OpenTelemetry Collector 配置檔案應該存在")
                .isTrue();

        Yaml yaml = new Yaml();
        Map<String, Object> config;
        
        try (FileInputStream inputStream = new FileInputStream(configPath.toFile())) {
            config = yaml.load(inputStream);
        }

        // 檢查批次處理器配置
        Map<String, Object> processors = (Map<String, Object>) config.get("processors");
        assertThat(processors)
                .as("配置應該包含處理器")
                .isNotNull()
                .containsKey("batch");

        Map<String, Object> batchConfig = (Map<String, Object>) processors.get("batch");
        
        // 檢查批次大小配置
        if (batchConfig.containsKey("send_batch_size")) {
            Integer batchSize = (Integer) batchConfig.get("send_batch_size");
            assertThat(batchSize)
                    .as("批次大小應該足夠大以提高效率")
                    .isGreaterThanOrEqualTo(MIN_BATCH_SIZE);
        }

        // 檢查批次超時配置
        if (batchConfig.containsKey("timeout")) {
            String timeout = (String) batchConfig.get("timeout");
            // 解析超時值（例如 "1s", "30s"）
            int timeoutMs = parseTimeoutToMs(timeout);
            assertThat(timeoutMs)
                    .as("批次超時應該在合理範圍內")
                    .isBetween(1000, MAX_BATCH_TIMEOUT_MS);
        }
    }

    /**
     * 屬性 4: 批次處理效率
     * 對於任何 微服務的 OpenTelemetry 配置，指標導出間隔應該適合批次處理
     */
    @Property(tries = 20)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 4: 批次處理效率")
    void microserviceMetricExportIntervalShouldSupportBatching(@ForAll("serviceConfigFiles") String configFile) throws IOException {
        Path configPath = Paths.get(configFile);
        
        if (!configPath.toFile().exists()) {
            // 如果配置檔案不存在，跳過此測試
            return;
        }

        String content = Files.readString(configPath);
        
        // 檢查指標導出間隔配置
        if (content.contains("otel.metric.export.interval")) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("otel.metric.export.interval") && !line.trim().startsWith("#")) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        int intervalMs = Integer.parseInt(parts[1].trim());
                        assertThat(intervalMs)
                                .as("指標導出間隔應該足夠長以支援批次處理")
                                .isGreaterThanOrEqualTo(MIN_EXPORT_INTERVAL_MS);
                    }
                }
            }
        }

        // 檢查批次大小配置
        if (content.contains("otel.metric.export.batch.size")) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("otel.metric.export.batch.size") && !line.trim().startsWith("#")) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) {
                        int batchSize = Integer.parseInt(parts[1].trim());
                        assertThat(batchSize)
                                .as("指標批次大小應該足夠大以提高效率")
                                .isGreaterThanOrEqualTo(MIN_BATCH_SIZE);
                    }
                }
            }
        }
    }

    /**
     * 測試批次處理配置的一致性
     */
    @Property(tries = 10)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 4: 批次配置一致性")
    void batchConfigurationShouldBeConsistentAcrossServices(@ForAll("serviceNames") String serviceName) throws IOException {
        String configFile = "../" + serviceName + "/otel-config.properties";
        Path configPath = Paths.get(configFile);
        
        if (!configPath.toFile().exists()) {
            return;
        }

        String content = Files.readString(configPath);
        
        // 檢查是否啟用了批次處理相關配置
        assertThat(content)
                .as("服務 " + serviceName + " 應該包含批次處理配置")
                .contains("otel.bsp.max.export.batch.size");
                
        assertThat(content)
                .as("服務 " + serviceName + " 應該包含批次超時配置")
                .contains("otel.bsp.export.timeout");
    }

    /**
     * 測試並發批次處理效率
     */
    @Property(tries = 5)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 4: 並發批次處理效率")
    void concurrentBatchProcessingShouldBeEfficient(@ForAll @IntRange(min = 2, max = 10) int threadCount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        try {
            // 模擬並發讀取配置檔案（代表並發處理遙測數據）
            List<CompletableFuture<Boolean>> futures = java.util.stream.IntStream.range(0, threadCount)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            // 模擬批次處理操作
                            Thread.sleep(100); // 模擬處理時間
                            
                            // 檢查配置檔案是否可以並發讀取
                            Path configPath = Paths.get(OTEL_COLLECTOR_CONFIG_PATH);
                            return configPath.toFile().exists() && Files.readString(configPath).contains("batch");
                        } catch (Exception e) {
                            return false;
                        }
                    }, executor))
                    .toList();

            // 等待所有任務完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            
            allFutures.get(5, TimeUnit.SECONDS);
            
            // 檢查所有任務都成功完成
            long successCount = futures.stream()
                    .mapToLong(future -> {
                        try {
                            return future.get() ? 1 : 0;
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();
                    
            assertThat(successCount)
                    .as("並發批次處理應該全部成功")
                    .isEqualTo(threadCount);
                    
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * 測試記憶體效率配置
     */
    @Test
    void memoryEfficiencyConfigurationShouldBeOptimal() throws IOException {
        Path configPath = Paths.get(OTEL_COLLECTOR_CONFIG_PATH);
        String content = Files.readString(configPath);
        
        // 檢查記憶體限制器配置
        assertThat(content)
                .as("配置應該包含記憶體限制器")
                .contains("memory_limiter");
                
        // 檢查批次處理配置存在
        assertThat(content)
                .as("配置應該包含批次處理器")
                .contains("batch:");
                
        // 檢查批次大小配置
        assertThat(content)
                .as("配置應該包含批次大小設定")
                .contains("send_batch_size");
    }

    /**
     * 解析超時字串為毫秒
     */
    private int parseTimeoutToMs(String timeout) {
        if (timeout.endsWith("s")) {
            return Integer.parseInt(timeout.substring(0, timeout.length() - 1)) * 1000;
        } else if (timeout.endsWith("ms")) {
            return Integer.parseInt(timeout.substring(0, timeout.length() - 2));
        } else if (timeout.endsWith("m")) {
            return Integer.parseInt(timeout.substring(0, timeout.length() - 1)) * 60 * 1000;
        }
        // 預設假設是秒
        return Integer.parseInt(timeout) * 1000;
    }

    /**
     * 生成服務配置檔案路徑
     */
    @Provide
    Arbitrary<String> serviceConfigFiles() {
        return Arbitraries.of(
                "../product-service/otel-config.properties",
                "../order-service/otel-config.properties",
                "../inventory-service/otel-config.properties",
                "../auth-service/otel-config.properties",
                "../api-gateway/otel-config.properties",
                "../eureka-server/otel-config.properties",
                "../config-server/otel-config.properties"
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
}