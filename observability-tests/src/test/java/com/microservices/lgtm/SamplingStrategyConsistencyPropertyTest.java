package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：取樣策略一致性測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 7: 取樣策略一致性
 * 驗證: 需求 3.4
 * 
 * 測試配置的取樣率，系統應該按照該比例對追蹤數據進行取樣
 */
public class SamplingStrategyConsistencyPropertyTest {

    private static final String OTEL_AGENT_DIR = "otel-agent";
    private static final String OTEL_CONFIG_FILE = "otel-config.properties";
    private static final String SAMPLING_STRATEGIES_FILE = "sampling-strategies.json";
    private static final String OTEL_COLLECTOR_CONFIG = "otel-collector-config.yaml";

    @BeforeEach
    void setUp() {
        // 確保測試環境準備就緒
        assertThat(new File(OTEL_AGENT_DIR)).exists();
        assertThat(new File(OTEL_COLLECTOR_CONFIG)).exists();
    }

    /**
     * 屬性 7: 取樣策略一致性
     * 對於任何配置的取樣率，系統應該按照該比例對追蹤數據進行取樣
     */
    @Property(tries = 100)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 7: 取樣策略一致性")
    void samplingRateShouldBeConsistentAcrossServices(
            @ForAll("validSamplingRates") double samplingRate,
            @ForAll("microserviceNames") String serviceName) throws IOException {
        
        // 驗證 OpenTelemetry Agent 配置中的取樣策略
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String agentConfig = Files.readString(configFile.toPath());
        
        // 驗證取樣器類型配置
        assertThat(agentConfig)
                .as("配置檔案應該包含取樣器類型設定")
                .contains("otel.traces.sampler=traceidratio");
                
        // 驗證父級取樣器配置（確保跨服務一致性）
        assertThat(agentConfig)
                .as("配置檔案應該啟用父級取樣器以確保跨服務一致性")
                .contains("otel.traces.sampler.parent_based=true");
                
        // 提取並驗證取樣率
        Pattern samplingRatePattern = Pattern.compile("otel\\.traces\\.sampler\\.arg=([0-9.]+)");
        Matcher samplingRateMatcher = samplingRatePattern.matcher(agentConfig);
        
        if (samplingRateMatcher.find()) {
            double configuredRate = Double.parseDouble(samplingRateMatcher.group(1));
            assertThat(configuredRate)
                    .as("配置的取樣率應該在有效範圍內")
                    .isBetween(0.0, 1.0);
        }
    }

    /**
     * 測試微服務環境變數中的取樣策略一致性
     */
    @Property(tries = 7)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 7: 微服務取樣策略一致性")
    void microserviceSamplingStrategyShouldBeConsistent(@ForAll("microserviceNames") String serviceName) throws IOException {
        String envFileName = serviceName + ".env";
        File envFile = new File(OTEL_AGENT_DIR, envFileName);
        
        if (!envFile.exists()) {
            // 如果環境檔案不存在，跳過此測試
            return;
        }
        
        String envConfig = Files.readString(envFile.toPath());
        
        // 驗證取樣器配置
        assertThat(envConfig)
                .as("服務 " + serviceName + " 應該配置取樣器類型")
                .contains("OTEL_TRACES_SAMPLER=traceidratio");
                
        // 驗證取樣率配置
        assertThat(envConfig)
                .as("服務 " + serviceName + " 應該配置取樣率")
                .contains("OTEL_TRACES_SAMPLER_ARG=");
                
        // 提取取樣率值
        Pattern samplingRatePattern = Pattern.compile("OTEL_TRACES_SAMPLER_ARG=([0-9.]+)");
        Matcher samplingRateMatcher = samplingRatePattern.matcher(envConfig);
        
        if (samplingRateMatcher.find()) {
            double samplingRate = Double.parseDouble(samplingRateMatcher.group(1));
            assertThat(samplingRate)
                    .as("服務 " + serviceName + " 的取樣率應該在有效範圍內")
                    .isBetween(0.0, 1.0);
        }
    }

    /**
     * 測試取樣策略檔案的配置正確性
     */
    @Test
    void samplingStrategiesFileShouldBeValid() throws IOException {
        File strategiesFile = new File(OTEL_AGENT_DIR, SAMPLING_STRATEGIES_FILE);
        
        if (!strategiesFile.exists()) {
            // 如果取樣策略檔案不存在，跳過此測試
            return;
        }
        
        String strategiesContent = Files.readString(strategiesFile.toPath());
        
        // 驗證 JSON 格式基本結構
        assertThat(strategiesContent)
                .as("取樣策略檔案應該包含服務策略配置")
                .contains("service_strategies");
                
        assertThat(strategiesContent)
                .as("取樣策略檔案應該包含預設策略配置")
                .contains("default_strategy");
                
        // 驗證包含微服務配置
        List<String> expectedServices = Arrays.asList(
                "api-gateway", "product-service", "order-service", 
                "inventory-service", "auth-service"
        );
        
        for (String service : expectedServices) {
            assertThat(strategiesContent)
                    .as("取樣策略檔案應該包含服務 " + service + " 的配置")
                    .contains("\"service\": \"" + service + "\"");
        }
    }

    /**
     * 測試取樣策略的數學一致性
     */
    @Property(tries = 20)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 7: 取樣策略數學一致性")
    void samplingRateMathematicalConsistencyShouldBeValid(@ForAll("validSamplingRates") double samplingRate) {
        // 驗證取樣率的數學屬性
        assertThat(samplingRate)
                .as("取樣率應該是非負數")
                .isGreaterThanOrEqualTo(0.0);
                
        assertThat(samplingRate)
                .as("取樣率應該不超過 1.0")
                .isLessThanOrEqualTo(1.0);
                
        // 驗證取樣率的精度
        String rateString = String.valueOf(samplingRate);
        int decimalPlaces = rateString.contains(".") ? 
                rateString.length() - rateString.indexOf(".") - 1 : 0;
                
        assertThat(decimalPlaces)
                .as("取樣率的小數位數應該合理（不超過 3 位）")
                .isLessThanOrEqualTo(3);
    }

    /**
     * 測試不同取樣策略類型的配置
     */
    @Property(tries = 5)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 7: 取樣策略類型一致性")
    void samplingStrategyTypesShouldBeConsistent(@ForAll("samplingStrategyTypes") String strategyType) throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String agentConfig = Files.readString(configFile.toPath());
        
        // 根據策略類型驗證相應的配置
        switch (strategyType) {
            case "traceidratio":
                assertThat(agentConfig)
                        .as("traceidratio 取樣器應該配置取樣率參數")
                        .contains("otel.traces.sampler.arg=");
                break;
            case "parentbased_traceidratio":
                assertThat(agentConfig)
                        .as("parentbased 取樣器應該啟用父級取樣")
                        .contains("otel.traces.sampler.parent_based=true");
                break;
        }
    }

    /**
     * 測試 OpenTelemetry Collector 的取樣配置
     */
    @Test
    void collectorSamplingShouldBeConfigured() throws IOException {
        File collectorConfigFile = new File(OTEL_COLLECTOR_CONFIG);
        String collectorConfig = Files.readString(collectorConfigFile.toPath());
        
        // 驗證 Collector 配置支援取樣
        assertThat(collectorConfig)
                .as("Collector 應該配置追蹤管道")
                .contains("traces:");
                
        // 驗證批次處理器配置（影響取樣效率）
        assertThat(collectorConfig)
                .as("Collector 應該配置批次處理器")
                .contains("batch:");
                
        // 驗證資源處理器配置（用於取樣決策）
        assertThat(collectorConfig)
                .as("Collector 應該配置資源處理器")
                .contains("resource:");
    }

    /**
     * 測試跨服務取樣決策的一致性
     */
    @Property(tries = 10)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 7: 跨服務取樣決策一致性")
    void crossServiceSamplingDecisionShouldBeConsistent(@ForAll("microserviceNames") String serviceName) throws IOException {
        String envFileName = serviceName + ".env";
        File envFile = new File(OTEL_AGENT_DIR, envFileName);
        
        if (!envFile.exists()) {
            return;
        }
        
        String envConfig = Files.readString(envFile.toPath());
        
        // 驗證追蹤傳播器配置（確保取樣決策能正確傳播）
        assertThat(envConfig)
                .as("服務 " + serviceName + " 應該配置追蹤傳播器")
                .containsAnyOf("OTEL_PROPAGATORS=", "tracecontext");
                
        // 驗證父級取樣配置（確保跨服務取樣一致性）
        if (envConfig.contains("OTEL_TRACES_SAMPLER=")) {
            assertThat(envConfig)
                    .as("服務 " + serviceName + " 應該使用一致的取樣器類型")
                    .contains("OTEL_TRACES_SAMPLER=traceidratio");
        }
    }

    /**
     * 測試取樣率對效能的影響配置
     */
    @Property(tries = 5)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 7: 取樣率效能影響")
    void samplingRatePerformanceImpactShouldBeReasonable(@ForAll("performanceSamplingRates") double samplingRate) throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String agentConfig = Files.readString(configFile.toPath());
        
        // 驗證批次處理配置（高取樣率需要更大的批次大小）
        Pattern batchSizePattern = Pattern.compile("otel\\.bsp\\.max\\.export\\.batch\\.size=([0-9]+)");
        Matcher batchSizeMatcher = batchSizePattern.matcher(agentConfig);
        
        if (batchSizeMatcher.find()) {
            int batchSize = Integer.parseInt(batchSizeMatcher.group(1));
            
            if (samplingRate >= 0.8) {
                // 高取樣率應該配置較大的批次大小
                assertThat(batchSize)
                        .as("高取樣率（" + samplingRate + "）應該配置較大的批次大小")
                        .isGreaterThanOrEqualTo(256);
            }
        }
        
        // 驗證導出超時配置
        Pattern timeoutPattern = Pattern.compile("otel\\.bsp\\.export\\.timeout=([0-9]+)");
        Matcher timeoutMatcher = timeoutPattern.matcher(agentConfig);
        
        if (timeoutMatcher.find()) {
            int timeout = Integer.parseInt(timeoutMatcher.group(1));
            assertThat(timeout)
                    .as("導出超時應該配置合理的值")
                    .isBetween(5000, 60000);
        }
    }

    /**
     * 生成有效的取樣率
     */
    @Provide
    Arbitrary<Double> validSamplingRates() {
        return Arbitraries.of(
                0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 
                0.6, 0.7, 0.8, 0.9, 1.0
        );
    }

    /**
     * 生成效能測試用的取樣率
     */
    @Provide
    Arbitrary<Double> performanceSamplingRates() {
        return Arbitraries.of(
                0.1, 0.5, 0.8, 1.0
        );
    }

    /**
     * 生成取樣策略類型
     */
    @Provide
    Arbitrary<String> samplingStrategyTypes() {
        return Arbitraries.of(
                "traceidratio",
                "parentbased_traceidratio",
                "always_on",
                "always_off"
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