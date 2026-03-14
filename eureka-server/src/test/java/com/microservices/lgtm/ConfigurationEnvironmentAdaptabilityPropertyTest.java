package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置環境適應性屬性測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 11: 配置環境適應性
 * 驗證: 需求 6.1, 6.2
 * 
 * 屬性: 對於任何環境變數配置的 LGTM 端點，系統應該使用該端點進行數據導出
 */
@SpringBootTest(classes = com.microservices.eureka.EurekaServerApplication.class)
@TestPropertySource(properties = {
    "logging.level.com.microservices.lgtm=DEBUG"
})
public class ConfigurationEnvironmentAdaptabilityPropertyTest {

    private Path projectRoot;
    private Path configManagementDir;
    private Path generatedConfigsDir;
    
    // 支援的環境列表
    private static final List<String> SUPPORTED_ENVIRONMENTS = Arrays.asList(
        "development", "testing", "staging", "production"
    );
    
    // 支援的服務列表
    private static final List<String> SUPPORTED_SERVICES = Arrays.asList(
        "api-gateway", "auth-service", "config-server", "eureka-server",
        "inventory-service", "order-service", "product-service"
    );
    
    // OTLP 端點模式
    private static final Pattern OTLP_ENDPOINT_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9.-]+:[0-9]+$"
    );

    @BeforeEach
    void setUp() {
        // 獲取項目根目錄
        projectRoot = Paths.get("").toAbsolutePath();
        while (!Files.exists(projectRoot.resolve("pom.xml")) && projectRoot.getParent() != null) {
            projectRoot = projectRoot.getParent();
        }
        
        configManagementDir = projectRoot.resolve("otel-agent/config-management");
        generatedConfigsDir = configManagementDir.resolve("generated-configs");
        
        // 確保配置管理目錄存在
        if (!Files.exists(configManagementDir)) {
            throw new RuntimeException("配置管理目錄不存在: " + configManagementDir);
        }
        
        // 確保生成配置目錄存在
        try {
            Files.createDirectories(generatedConfigsDir);
        } catch (IOException e) {
            throw new RuntimeException("無法創建生成配置目錄", e);
        }
    }

    /**
     * 屬性測試: 環境特定端點配置適應性
     * 
     * 對於任何支援的環境，生成的配置應該包含該環境特定的 OTLP 端點
     */
    @Property(tries = 20)
    @Label("環境特定端點配置適應性")
    void environmentSpecificEndpointAdaptability(
            @ForAll("supportedEnvironments") String environment,
            @ForAll("supportedServices") String service) {
        
        // 生成配置檔案
        generateServiceConfig(service, environment);
        
        // 讀取生成的配置檔案
        Path configPath = generatedConfigsDir.resolve(service + "-" + environment + ".env");
        List<String> configLines = readConfigFile(configPath);
        
        // 提取 OTLP 端點配置
        Optional<String> otlpEndpoint = extractConfigValue(configLines, "OTEL_EXPORTER_OTLP_ENDPOINT");
        
        // 驗證端點存在且格式正確
        assertThat(otlpEndpoint)
            .as("OTLP 端點應該存在於配置中")
            .isPresent();
        
        String endpoint = otlpEndpoint.get();
        
        // 驗證端點格式
        assertThat(endpoint)
            .as("OTLP 端點格式應該正確")
            .matches(OTLP_ENDPOINT_PATTERN);
        
        // 驗證環境特定的端點配置
        verifyEnvironmentSpecificEndpoint(environment, endpoint);
    }

    /**
     * 屬性測試: 服務特定配置適應性
     * 
     * 對於任何支援的服務，生成的配置應該包含該服務特定的儀表化設定
     */
    @Property(tries = 20)
    @Label("服務特定配置適應性")
    void serviceSpecificConfigurationAdaptability(
            @ForAll("supportedServices") String service,
            @ForAll("supportedEnvironments") String environment) {
        
        // 生成配置檔案
        generateServiceConfig(service, environment);
        
        // 讀取生成的配置檔案
        Path configPath = generatedConfigsDir.resolve(service + "-" + environment + ".env");
        List<String> configLines = readConfigFile(configPath);
        
        // 驗證服務名稱配置
        Optional<String> serviceName = extractConfigValue(configLines, "OTEL_SERVICE_NAME");
        assertThat(serviceName)
            .as("服務名稱應該存在於配置中")
            .isPresent()
            .hasValue(service);
        
        // 驗證服務特定的儀表化配置
        verifyServiceSpecificInstrumentation(service, configLines);
    }

    /**
     * 屬性測試: 配置檔案完整性
     * 
     * 對於任何生成的配置檔案，都應該包含所有必要的 OpenTelemetry 配置項
     */
    @Property(tries = 10)
    @Label("配置檔案完整性")
    void configurationFileCompleteness(
            @ForAll("supportedServices") String service,
            @ForAll("supportedEnvironments") String environment) {
        
        // 生成配置檔案
        generateServiceConfig(service, environment);
        
        // 讀取生成的配置檔案
        Path configPath = generatedConfigsDir.resolve(service + "-" + environment + ".env");
        List<String> configLines = readConfigFile(configPath);
        
        // 必要的配置項列表
        List<String> requiredConfigs = Arrays.asList(
            "OTEL_SERVICE_NAME",
            "OTEL_SERVICE_VERSION",
            "OTEL_EXPORTER_OTLP_ENDPOINT",
            "OTEL_EXPORTER_OTLP_PROTOCOL",
            "OTEL_TRACES_EXPORTER",
            "OTEL_METRICS_EXPORTER",
            "OTEL_LOGS_EXPORTER",
            "JAVA_TOOL_OPTIONS"
        );
        
        // 驗證所有必要配置項都存在
        for (String requiredConfig : requiredConfigs) {
            Optional<String> configValue = extractConfigValue(configLines, requiredConfig);
            assertThat(configValue)
                .as("必要配置項 %s 應該存在", requiredConfig)
                .isPresent();
            
            assertThat(configValue.get())
                .as("配置項 %s 不應該為空", requiredConfig)
                .isNotBlank();
        }
    }

    /**
     * 單元測試: 驗證配置生成工具存在且可執行
     */
    @Test
    void configurationGenerationToolExists() {
        Path generateScript = configManagementDir.resolve("generate-configs.sh");
        assertThat(generateScript)
            .as("配置生成腳本應該存在")
            .exists();
        
        assertThat(Files.isExecutable(generateScript))
            .as("配置生成腳本應該可執行")
            .isTrue();
    }

    /**
     * 單元測試: 驗證環境配置檔案存在
     */
    @Test
    void environmentConfigurationFilesExist() {
        for (String environment : SUPPORTED_ENVIRONMENTS) {
            Path envFile = configManagementDir.resolve("environments").resolve(environment + ".env");
            assertThat(envFile)
                .as("環境配置檔案應該存在: %s", environment)
                .exists();
        }
    }

    /**
     * 單元測試: 驗證服務模板檔案存在
     */
    @Test
    void serviceTemplateFilesExist() {
        for (String service : SUPPORTED_SERVICES) {
            Path templateFile = configManagementDir.resolve("services").resolve(service + ".template");
            assertThat(templateFile)
                .as("服務模板檔案應該存在: %s", service)
                .exists();
        }
    }

    // ==================== 輔助方法 ====================

    /**
     * 生成服務配置檔案
     */
    private void generateServiceConfig(String service, String environment) {
        try {
            Path scriptPath = configManagementDir.resolve("generate-configs.sh");
            
            if (!Files.exists(scriptPath)) {
                throw new RuntimeException("配置生成腳本不存在: " + scriptPath);
            }
            
            // 執行配置生成腳本
            ProcessBuilder pb = new ProcessBuilder(
                scriptPath.toString(),
                environment,
                service
            );
            pb.directory(projectRoot.toFile());
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new RuntimeException("配置生成失敗，退出碼: " + exitCode);
            }
            
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("執行配置生成腳本失敗", e);
        }
    }

    /**
     * 讀取配置檔案
     */
    private List<String> readConfigFile(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                throw new RuntimeException("配置檔案不存在: " + configPath);
            }
            return Files.readAllLines(configPath);
        } catch (IOException e) {
            throw new RuntimeException("無法讀取配置檔案: " + configPath, e);
        }
    }

    /**
     * 從配置行中提取指定配置項的值
     */
    private Optional<String> extractConfigValue(List<String> configLines, String configKey) {
        return configLines.stream()
            .filter(line -> line.startsWith(configKey + "="))
            .map(line -> line.substring(configKey.length() + 1))
            .map(value -> value.replaceAll("^[\"']|[\"']$", "")) // 移除引號
            .findFirst();
    }

    /**
     * 驗證環境特定的端點配置
     */
    private void verifyEnvironmentSpecificEndpoint(String environment, String endpoint) {
        switch (environment) {
            case "development":
                assertThat(endpoint)
                    .as("開發環境應該使用本地端點")
                    .contains("localhost");
                break;
            case "testing":
                assertThat(endpoint)
                    .as("測試環境應該使用測試端點")
                    .contains("test");
                break;
            case "staging":
                assertThat(endpoint)
                    .as("預發布環境應該使用預發布端點")
                    .contains("staging");
                break;
            case "production":
                assertThat(endpoint)
                    .as("生產環境應該使用生產端點")
                    .doesNotContain("localhost")
                    .doesNotContain("test")
                    .doesNotContain("staging");
                break;
        }
    }

    /**
     * 驗證服務特定的儀表化配置
     */
    private void verifyServiceSpecificInstrumentation(String service, List<String> configLines) {
        switch (service) {
            case "api-gateway":
                assertThat(findConfigLine(configLines, "OTEL_INSTRUMENTATION_SPRING_CLOUD_GATEWAY_ENABLED"))
                    .as("API Gateway 應該啟用 Spring Cloud Gateway 儀表化")
                    .isPresent();
                break;
            case "auth-service":
                assertThat(findConfigLine(configLines, "OTEL_INSTRUMENTATION_SPRING_SECURITY_ENABLED"))
                    .as("Auth Service 應該啟用 Spring Security 儀表化")
                    .isPresent();
                break;
            case "inventory-service":
                assertThat(findConfigLine(configLines, "OTEL_INSTRUMENTATION_JEDIS_ENABLED"))
                    .as("Inventory Service 應該啟用 Redis 儀表化")
                    .isPresent();
                break;
            case "order-service":
                assertThat(findConfigLine(configLines, "OTEL_INSTRUMENTATION_OPENFEIGN_ENABLED"))
                    .as("Order Service 應該啟用 OpenFeign 儀表化")
                    .isPresent();
                break;
        }
    }

    /**
     * 查找包含指定配置項的行
     */
    private Optional<String> findConfigLine(List<String> configLines, String configKey) {
        return configLines.stream()
            .filter(line -> line.startsWith(configKey + "="))
            .findFirst();
    }

    // ==================== 資料提供者 ====================

    @Provide
    Arbitrary<String> supportedEnvironments() {
        return Arbitraries.of(SUPPORTED_ENVIRONMENTS);
    }

    @Provide
    Arbitrary<String> supportedServices() {
        return Arbitraries.of(SUPPORTED_SERVICES);
    }
}