package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：OpenTelemetry 自動儀表化完整性測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 1: 自動儀表化完整性
 * 驗證: 需求 1.1, 1.3, 1.4
 * 
 * 測試 OpenTelemetry Java Agent 的自動儀表化配置完整性
 */
public class AutoInstrumentationIntegrityPropertyTest {

    private static final String OTEL_AGENT_DIR = "otel-agent";
    private static final String OTEL_JAR_FILE = "opentelemetry-javaagent.jar";
    private static final String OTEL_CONFIG_FILE = "otel-config.properties";

    @BeforeEach
    void setUp() {
        // 確保測試環境準備就緒
        assertThat(new File(OTEL_AGENT_DIR)).exists();
    }

    /**
     * 測試 OpenTelemetry Java Agent JAR 檔案存在且有效
     */
    @Test
    void openTelemetryJavaAgentShouldExistAndBeValid() {
        File jarFile = new File(OTEL_AGENT_DIR, OTEL_JAR_FILE);
        
        assertThat(jarFile.exists())
                .as("OpenTelemetry Java Agent JAR 檔案應該存在")
                .isTrue();
                
        assertThat(jarFile.length())
                .as("OpenTelemetry Java Agent JAR 檔案應該不為空")
                .isGreaterThan(1024 * 1024); // 至少 1MB
                
        assertThat(jarFile.getName())
                .as("JAR 檔案應該有正確的檔名")
                .endsWith(".jar");
    }

    /**
     * 測試統一配置檔案存在且包含必要配置
     */
    @Test
    void otelConfigurationFileShouldExistAndBeComplete() throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        
        assertThat(configFile.exists())
                .as("OpenTelemetry 配置檔案應該存在")
                .isTrue();
                
        String content = Files.readString(configFile.toPath());
        
        // 檢查關鍵配置項目
        assertThat(content)
                .as("配置檔案應該包含 OTLP 端點配置")
                .contains("otel.exporter.otlp.endpoint");
                
        assertThat(content)
                .as("配置檔案應該包含指標導出器配置")
                .contains("otel.metrics.exporter");
                
        assertThat(content)
                .as("配置檔案應該包含追蹤導出器配置")
                .contains("otel.traces.exporter");
                
        assertThat(content)
                .as("配置檔案應該包含日誌導出器配置")
                .contains("otel.logs.exporter");
    }

    /**
     * 屬性 1: 自動儀表化完整性
     * 對於任何微服務，都應該有對應的 OpenTelemetry 環境配置檔案
     */
    @Property(tries = 10)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 1: 自動儀表化完整性")
    void everyMicroserviceShouldHaveOtelConfiguration(@ForAll("microserviceNames") String serviceName) throws IOException {
        String envFileName = serviceName + ".env";
        File envFile = new File(OTEL_AGENT_DIR, envFileName);
        
        assertThat(envFile.exists())
                .as("微服務 " + serviceName + " 應該有對應的 OpenTelemetry 環境配置檔案")
                .isTrue();
                
        String content = Files.readString(envFile.toPath());
        
        // 驗證必要的環境變數
        assertThat(content)
                .as("配置檔案應該包含服務名稱設定")
                .contains("OTEL_SERVICE_NAME=" + serviceName);
                
        assertThat(content)
                .as("配置檔案應該包含 OTLP 端點設定")
                .contains("OTEL_EXPORTER_OTLP_ENDPOINT");
                
        assertThat(content)
                .as("配置檔案應該包含 Java Agent 設定")
                .contains("JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar");
                
        assertThat(content)
                .as("配置檔案應該包含配置檔案路徑設定")
                .contains("OTEL_JAVAAGENT_CONFIGURATION_FILE=/app/otel-config.properties");
    }

    /**
     * 測試所有必要的儀表化功能都已啟用
     */
    @Property(tries = 5)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 1: 儀表化功能啟用")
    void requiredInstrumentationShouldBeEnabled(@ForAll("instrumentationTypes") String instrumentationType) throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        assertThat(content)
                .as("儀表化類型 " + instrumentationType + " 應該被啟用")
                .contains(instrumentationType + ".enabled=true");
    }

    /**
     * 測試環境變數模板檔案的完整性
     */
    @Test
    void environmentTemplateFileShouldBeComplete() throws IOException {
        File templateFile = new File(OTEL_AGENT_DIR, "otel-env-template.env");
        
        assertThat(templateFile.exists())
                .as("環境變數模板檔案應該存在")
                .isTrue();
                
        String content = Files.readString(templateFile.toPath());
        
        // 檢查模板包含所有必要的變數
        String[] requiredVars = {
                "OTEL_SERVICE_NAME",
                "OTEL_SERVICE_VERSION",
                "OTEL_RESOURCE_ATTRIBUTES",
                "OTEL_EXPORTER_OTLP_ENDPOINT",
                "OTEL_METRICS_EXPORTER",
                "OTEL_TRACES_EXPORTER",
                "OTEL_LOGS_EXPORTER",
                "JAVA_TOOL_OPTIONS"
        };
        
        for (String var : requiredVars) {
            assertThat(content)
                    .as("模板檔案應該包含變數 " + var)
                    .contains(var + "=");
        }
    }

    /**
     * 測試配置檔案中的效能調優參數
     */
    @Property(tries = 3)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 1: 效能調優配置")
    void performanceTuningParametersShouldBeConfigured(@ForAll("performanceParameters") String parameter) throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        assertThat(content)
                .as("效能調優參數 " + parameter + " 應該被配置")
                .contains(parameter);
    }

    /**
     * 測試 JVM 指標收集配置
     */
    @Test
    void jvmMetricsCollectionShouldBeEnabled() throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        // 驗證 JVM 指標相關配置
        assertThat(content)
                .as("JVM 運行時遙測應該被啟用")
                .contains("otel.instrumentation.runtime-telemetry.enabled=true");
                
        assertThat(content)
                .as("Java 8 運行時遙測應該被啟用")
                .contains("otel.instrumentation.runtime-telemetry-java8.enabled=true");
    }

    /**
     * 測試 HTTP 和資料庫儀表化配置
     */
    @Test
    void httpAndDatabaseInstrumentationShouldBeEnabled() throws IOException {
        File configFile = new File(OTEL_AGENT_DIR, OTEL_CONFIG_FILE);
        String content = Files.readString(configFile.toPath());
        
        // HTTP 儀表化
        assertThat(content)
                .as("HTTP 儀表化應該被啟用")
                .contains("otel.instrumentation.http.enabled=true");
                
        assertThat(content)
                .as("Servlet 儀表化應該被啟用")
                .contains("otel.instrumentation.servlet.enabled=true");
                
        assertThat(content)
                .as("Spring WebMVC 儀表化應該被啟用")
                .contains("otel.instrumentation.spring-webmvc.enabled=true");
                
        // 資料庫儀表化
        assertThat(content)
                .as("JDBC 儀表化應該被啟用")
                .contains("otel.instrumentation.jdbc.enabled=true");
                
        assertThat(content)
                .as("Hibernate 儀表化應該被啟用")
                .contains("otel.instrumentation.hibernate.enabled=true");
                
        assertThat(content)
                .as("JPA 儀表化應該被啟用")
                .contains("otel.instrumentation.jpa.enabled=true");
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
     * 生成儀表化類型
     */
    @Provide
    Arbitrary<String> instrumentationTypes() {
        return Arbitraries.of(
                "otel.instrumentation.runtime-telemetry",
                "otel.instrumentation.http",
                "otel.instrumentation.servlet",
                "otel.instrumentation.spring-webmvc",
                "otel.instrumentation.spring-web",
                "otel.instrumentation.jdbc",
                "otel.instrumentation.hibernate",
                "otel.instrumentation.jpa",
                "otel.instrumentation.spring-boot",
                "otel.instrumentation.spring-core",
                "otel.instrumentation.spring-data"
        );
    }

    /**
     * 生成效能調優參數
     */
    @Provide
    Arbitrary<String> performanceParameters() {
        return Arbitraries.of(
                "otel.bsp.max.export.batch.size",
                "otel.bsp.export.timeout",
                "otel.bsp.schedule.delay",
                "otel.metric.export.interval"
        );
    }
}