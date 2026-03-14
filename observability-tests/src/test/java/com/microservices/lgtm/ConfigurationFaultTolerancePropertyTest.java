package com.microservices.lgtm;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * 屬性 12: 配置容錯性
 * 
 * 驗證需求 6.3: 當配置無效時，系統應該使用預設值並記錄警告
 * 
 * 此測試驗證 OpenTelemetry 配置系統在面對各種無效配置時的容錯能力，
 * 確保系統能夠優雅地處理配置錯誤並繼續正常運行。
 */
public class ConfigurationFaultTolerancePropertyTest {

    @TempDir
    Path tempDir;
    
    private Path configValidatorScript;
    private Path configFallbackScript;
    
    @BeforeEach
    void setUp() throws IOException {
        // 設定配置管理腳本路徑
        configValidatorScript = Path.of("../otel-agent/config-management/config-validator.sh").toAbsolutePath();
        configFallbackScript = Path.of("../otel-agent/config-management/config-fallback.sh").toAbsolutePath();
        
        // 確保腳本存在且可執行
        if (!Files.exists(configValidatorScript)) {
            throw new RuntimeException("配置驗證器腳本不存在: " + configValidatorScript);
        }
        if (!Files.exists(configFallbackScript)) {
            throw new RuntimeException("配置容錯腳本不存在: " + configFallbackScript);
        }
    }

    /**
     * 屬性: 無效配置值應該被自動修復為預設值
     */
    @Property
    @Report(Reporting.GENERATED)
    void invalidConfigurationValuesShouldBeReplacedWithDefaults(
            @ForAll("invalidConfigValues") Map<String, String> invalidConfigs) {
        
        try {
            // 創建包含無效配置的臨時檔案
            Path configFile = createConfigFile(invalidConfigs);
            
            // 使用配置驗證器修復配置
            ProcessResult fixResult = executeScript(
                configValidatorScript.toString(), 
                "fix", 
                configFile.toString()
            );
            
            // 驗證修復操作成功
            Assertions.assertEquals(0, fixResult.exitCode, "配置修復應該成功");
            
            // 驗證修復後的配置檔案
            ProcessResult validateResult = executeScript(
                configValidatorScript.toString(),
                "validate",
                configFile.toString()
            );
            
            // 修復後的配置應該能通過驗證
            Assertions.assertEquals(0, validateResult.exitCode, "修復後的配置應該通過驗證");
            
            // 驗證配置檔案包含預設值
            String configContent = Files.readString(configFile);
            for (String key : invalidConfigs.keySet()) {
                String defaultValue = getExpectedDefaultValue(key);
                if (defaultValue != null) {
                    Assertions.assertTrue(configContent.contains(key + "=" + defaultValue), 
                        "配置檔案應該包含預設值: " + key + "=" + defaultValue);
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("配置容錯性測試失敗", e);
        }
    }

    /**
     * 屬性: 缺少必要配置項時應該添加預設值
     */
    @Property
    @Report(Reporting.GENERATED)
    void missingRequiredConfigurationsShouldBeAddedWithDefaults(
            @ForAll("partialConfigs") Map<String, String> partialConfigs) {
        
        try {
            // 創建部分配置檔案（缺少必要項目）
            Path configFile = createConfigFile(partialConfigs);
            
            // 使用配置驗證器修復配置
            ProcessResult fixResult = executeScript(
                configValidatorScript.toString(),
                "fix",
                configFile.toString()
            );
            
            // 驗證修復操作成功
            Assertions.assertEquals(0, fixResult.exitCode, "配置修復應該成功");
            
            // 驗證必要配置項已被添加
            String configContent = Files.readString(configFile);
            
            // 檢查必要配置項
            List<String> requiredConfigs = List.of(
                "OTEL_SERVICE_NAME",
                "OTEL_EXPORTER_OTLP_ENDPOINT"
            );
            
            for (String requiredConfig : requiredConfigs) {
                if (!partialConfigs.containsKey(requiredConfig)) {
                    // 如果原本沒有這個配置，修復後應該添加預設值
                    Assertions.assertTrue(configContent.contains(requiredConfig + "="), 
                        "應該添加必要配置項: " + requiredConfig);
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("缺少配置項容錯測試失敗", e);
        }
    }

    /**
     * 屬性: 容錯處理腳本應該能載入有問題的配置並應用預設值
     */
    @Property
    @Report(Reporting.GENERATED)
    void faultToleranceScriptShouldHandleBrokenConfigurations(
            @ForAll("brokenConfigs") Map<String, String> brokenConfigs) {
        
        try {
            // 創建有問題的配置檔案
            Path configFile = createConfigFile(brokenConfigs);
            
            // 使用容錯處理腳本載入配置
            ProcessResult loadResult = executeScript(
                configFallbackScript.toString(),
                "load",
                configFile.toString()
            );
            
            // 容錯處理應該成功（退出碼 0 或 2 表示應用了容錯處理）
            Assertions.assertTrue(loadResult.exitCode == 0 || loadResult.exitCode == 2, 
                "容錯處理應該成功，退出碼: " + loadResult.exitCode);
            
            // 如果退出碼是 2，表示應用了容錯處理
            if (loadResult.exitCode == 2) {
                Assertions.assertTrue(loadResult.stderr.contains("容錯處理"), 
                    "應該包含容錯處理訊息");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("容錯處理腳本測試失敗", e);
        }
    }

    /**
     * 屬性: 預設配置載入應該總是成功
     */
    @Property
    @Report(Reporting.GENERATED)
    void defaultConfigurationLoadingShouldAlwaysSucceed() {
        try {
            // 載入預設配置
            ProcessResult defaultResult = executeScript(
                configFallbackScript.toString(),
                "default"
            );
            
            // 預設配置載入應該總是成功
            Assertions.assertEquals(0, defaultResult.exitCode, "預設配置載入應該成功");
            
            // 應該包含預設配置的警告訊息
            Assertions.assertTrue(defaultResult.stderr.contains("預設"), 
                "應該包含預設配置訊息");
            
        } catch (Exception e) {
            throw new RuntimeException("預設配置載入測試失敗", e);
        }
    }

    // 生成器：無效配置值
    @Provide
    Arbitrary<Map<String, String>> invalidConfigValues() {
        return Arbitraries.maps(
            Arbitraries.of(
                "OTEL_TRACES_SAMPLER_ARG",
                "OTEL_LOG_LEVEL", 
                "OTEL_JAVAAGENT_DEBUG",
                "OTEL_EXPORTER_OTLP_PROTOCOL",
                "OTEL_EXPORTER_OTLP_ENDPOINT"
            ),
            Arbitraries.oneOf(
                Arbitraries.strings().withCharRange('a', 'z').ofLength(5), // 無效字串
                Arbitraries.of("2.0", "-1", "999", "invalid", "maybe", "wrong"), // 無效值
                Arbitraries.of("", " ", "null") // 空值
            )
        ).ofMinSize(1).ofMaxSize(3);
    }

    // 生成器：部分配置（缺少必要項目）
    @Provide
    Arbitrary<Map<String, String>> partialConfigs() {
        return Arbitraries.maps(
            Arbitraries.of(
                "OTEL_TRACES_SAMPLER",
                "OTEL_LOG_LEVEL",
                "OTEL_JAVAAGENT_DEBUG"
            ),
            Arbitraries.of(
                "traceidratio",
                "INFO", 
                "false"
            )
        ).ofMinSize(0).ofMaxSize(2);
    }

    // 生成器：有問題的配置
    @Provide
    Arbitrary<Map<String, String>> brokenConfigs() {
        return Arbitraries.maps(
            Arbitraries.of(
                "OTEL_SERVICE_NAME",
                "OTEL_EXPORTER_OTLP_ENDPOINT",
                "OTEL_TRACES_SAMPLER_ARG",
                "OTEL_LOG_LEVEL"
            ),
            Arbitraries.oneOf(
                Arbitraries.of("", "invalid-url", "2.5", "INVALID_LEVEL"),
                Arbitraries.strings().withCharRange('!', '~').ofMaxLength(10)
            )
        ).ofMinSize(1).ofMaxSize(4);
    }

    // 輔助方法：創建配置檔案
    private Path createConfigFile(Map<String, String> configs) throws IOException {
        Path configFile = tempDir.resolve("test-config.env");
        List<String> lines = new ArrayList<>();
        
        lines.add("# 測試配置檔案");
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            lines.add(entry.getKey() + "=" + entry.getValue());
        }
        
        Files.write(configFile, lines);
        return configFile;
    }

    // 輔助方法：執行腳本
    private ProcessResult executeScript(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();
        
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        int exitCode = process.waitFor();
        
        return new ProcessResult(exitCode, stdout, stderr);
    }

    // 輔助方法：獲取預期的預設值
    private String getExpectedDefaultValue(String key) {
        Map<String, String> defaults = Map.of(
            "OTEL_TRACES_SAMPLER_ARG", "0.1",
            "OTEL_LOG_LEVEL", "INFO",
            "OTEL_JAVAAGENT_DEBUG", "false",
            "OTEL_EXPORTER_OTLP_PROTOCOL", "grpc",
            "OTEL_SERVICE_VERSION", "1.0.0"
        );
        return defaults.get(key);
    }

    // 輔助類：進程結果
    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        
        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}