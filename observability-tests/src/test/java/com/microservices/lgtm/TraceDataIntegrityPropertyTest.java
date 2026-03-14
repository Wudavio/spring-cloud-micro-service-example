package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：OpenTelemetry 追蹤資料完整性測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 2: 追蹤資料完整性
 * 驗證: 需求 1.2, 3.1, 3.3
 * 
 * 測試 OpenTelemetry 追蹤配置和跨服務追蹤關聯的完整性
 */
public class TraceDataIntegrityPropertyTest {

    private static final String OTEL_AGENT_DIR = "../otel-agent";
    private static final String OTEL_CONFIG_FILE = "otel-config.properties";

    @BeforeEach
    void setUp() {
        // 確保測試環境準備就緒
        assertThat(new File(OTEL_AGENT_DIR)).exists();
    }

    /**
     * 測試追蹤導出器配置正確性
     */
    @Test
    void traceExporterConfigurationShouldBeCorrect() throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        // 驗證追蹤導出器配置
        assertThat(content)
                .as("配置檔案應該包含 OTLP 追蹤導出器")
                .contains("otel.traces.exporter=otlp");
                
        assertThat(content)
                .as("配置檔案應該包含追蹤取樣器配置")
                .contains("otel.traces.sampler=traceidratio");
                
        assertThat(content)
                .as("配置檔案應該包含取樣率配置")
                .contains("otel.traces.sampler.arg=1.0");
    }

    /**
     * 屬性 2: 追蹤資料完整性
     * 對於任何微服務配置，都應該包含正確的追蹤相關環境變數
     */
    @Property(tries = 10)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 2: 追蹤資料完整性")
    void everyMicroserviceShouldHaveTraceConfiguration(@ForAll("microserviceNames") String serviceName) throws IOException {
        String envFileName = serviceName + ".env";
        File envFile = new File(OTEL_AGENT_DIR, envFileName);
        
        assertThat(envFile.exists())
                .as("微服務 " + serviceName + " 應該有環境配置檔案")
                .isTrue();
                
        String content = Files.readString(envFile.toPath());
        
        // 驗證追蹤相關的環境變數
        assertThat(content)
                .as("配置檔案應該包含追蹤導出器設定")
                .contains("OTEL_TRACES_EXPORTER=otlp");
                
        assertThat(content)
                .as("配置檔案應該包含追蹤取樣器設定")
                .contains("OTEL_TRACES_SAMPLER=traceidratio");
                
        assertThat(content)
                .as("配置檔案應該包含取樣率設定")
                .contains("OTEL_TRACES_SAMPLER_ARG=1.0");
                
        assertThat(content)
                .as("配置檔案應該包含 OTLP 端點設定")
                .contains("OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317");
    }

    /**
     * 測試跨服務追蹤關聯配置
     */
    @Property(tries = 5)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 2: 跨服務追蹤關聯")
    void crossServiceTracingConfigurationShouldBeConsistent(@ForAll("microserviceNames") String serviceName) throws IOException {
        String envFileName = serviceName + ".env";
        File envFile = new File(OTEL_AGENT_DIR, envFileName);
        String content = Files.readString(envFile.toPath());
        
        // 驗證服務名稱設定正確
        assertThat(content)
                .as("服務 " + serviceName + " 應該有正確的服務名稱設定")
                .contains("OTEL_SERVICE_NAME=" + serviceName);
                
        // 驗證資源屬性包含服務命名空間
        assertThat(content)
                .as("服務 " + serviceName + " 應該包含服務命名空間屬性")
                .contains("service.namespace=microservices");
                
        // 驗證服務實例 ID 包含服務名稱
        assertThat(content)
                .as("服務 " + serviceName + " 應該包含服務實例 ID")
                .contains("service.instance.id=" + serviceName + "-1");
    }

    /**
     * 測試 HTTP 和 Spring 儀表化配置（用於跨服務追蹤）
     */
    @Test
    void httpAndSpringInstrumentationForTracingShouldBeEnabled() throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        // HTTP 儀表化（用於跨服務請求追蹤）
        assertThat(content)
                .as("HTTP 儀表化應該被啟用以支援跨服務追蹤")
                .contains("otel.instrumentation.http.enabled=true");
                
        assertThat(content)
                .as("Spring WebMVC 儀表化應該被啟用")
                .contains("otel.instrumentation.spring-webmvc.enabled=true");
                
        assertThat(content)
                .as("Spring Web 儀表化應該被啟用")
                .contains("otel.instrumentation.spring-web.enabled=true");
                
        // Spring Boot 儀表化
        assertThat(content)
                .as("Spring Boot 儀表化應該被啟用")
                .contains("otel.instrumentation.spring-boot.enabled=true");
    }

    /**
     * 測試追蹤取樣率配置的有效性
     */
    @Property(tries = 3)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 2: 追蹤取樣率有效性")
    void traceSamplingRateShouldBeValid(@ForAll("samplingRates") String samplingRate) throws IOException {
        // 檢查配置檔案中的取樣率是否為有效值
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        // 提取配置檔案中的取樣率
        Pattern pattern = Pattern.compile("otel\\.traces\\.sampler\\.arg=([0-9.]+)");
        Matcher matcher = pattern.matcher(content);
        
        assertThat(matcher.find())
                .as("配置檔案應該包含取樣率設定")
                .isTrue();
                
        String configuredRate = matcher.group(1);
        double rate = Double.parseDouble(configuredRate);
        
        assertThat(rate)
                .as("取樣率應該在 0.0 到 1.0 之間")
                .isBetween(0.0, 1.0);
    }

    /**
     * 測試 Dockerfile 中的追蹤配置一致性
     */
    @Property(tries = 7)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 2: Dockerfile 追蹤配置一致性")
    void dockerfileTracingConfigurationShouldBeConsistent(@ForAll("microserviceNames") String serviceName) throws IOException {
        File dockerFile = new File("../" + serviceName + "/Dockerfile");
        
        if (!dockerFile.exists()) {
            // 如果 Dockerfile 不存在，跳過此測試
            return;
        }
        
        String content = Files.readString(dockerFile.toPath());
        
        // 驗證 Dockerfile 包含追蹤相關環境變數
        assertThat(content)
                .as("Dockerfile 應該包含追蹤導出器環境變數")
                .contains("ENV OTEL_TRACES_EXPORTER=otlp");
                
        assertThat(content)
                .as("Dockerfile 應該包含追蹤取樣器環境變數")
                .contains("ENV OTEL_TRACES_SAMPLER=traceidratio");
                
        assertThat(content)
                .as("Dockerfile 應該包含正確的服務名稱")
                .contains("ENV OTEL_SERVICE_NAME=" + serviceName);
                
        // 驗證 Java Agent 啟動參數
        assertThat(content)
                .as("Dockerfile 應該包含 OpenTelemetry Java Agent 啟動參數")
                .contains("-javaagent:/app/opentelemetry-javaagent.jar");
    }

    /**
     * 測試批次處理配置（影響追蹤資料導出效率）
     */
    @Test
    void batchProcessingConfigurationForTracesShouldBeOptimized() throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        // 驗證批次處理相關配置
        assertThat(content)
                .as("配置檔案應該包含批次導出大小設定")
                .contains("otel.bsp.max.export.batch.size");
                
        assertThat(content)
                .as("配置檔案應該包含導出超時設定")
                .contains("otel.bsp.export.timeout");
                
        assertThat(content)
                .as("配置檔案應該包含調度延遲設定")
                .contains("otel.bsp.schedule.delay");
                
        // 提取並驗證批次大小值
        Pattern batchSizePattern = Pattern.compile("otel\\.bsp\\.max\\.export\\.batch\\.size=([0-9]+)");
        Matcher batchSizeMatcher = batchSizePattern.matcher(content);
        
        if (batchSizeMatcher.find()) {
            int batchSize = Integer.parseInt(batchSizeMatcher.group(1));
            assertThat(batchSize)
                    .as("批次大小應該是合理的值（大於 0 且小於 10000）")
                    .isBetween(1, 10000);
        }
    }

    /**
     * 測試壓縮配置（影響追蹤資料傳輸效率）
     */
    @Test
    void compressionConfigurationShouldBeEnabled() throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        // 驗證壓縮配置
        assertThat(content)
                .as("配置檔案應該啟用 gzip 壓縮以提高傳輸效率")
                .contains("otel.exporter.otlp.compression=gzip");
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

    /**
     * 生成有效的取樣率
     */
    @Provide
    Arbitrary<String> samplingRates() {
        return Arbitraries.of(
                "0.0",
                "0.1", 
                "0.5",
                "1.0"
        );
    }
}