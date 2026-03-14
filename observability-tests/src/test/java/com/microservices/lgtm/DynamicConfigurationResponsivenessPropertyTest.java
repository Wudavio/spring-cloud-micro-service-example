package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 屬性 13: 動態配置響應性
 * 
 * 驗證需求 6.4: 系統應該支援動態調整取樣率和導出頻率
 * 
 * 此測試驗證 OpenTelemetry 配置系統能夠響應配置變更，
 * 並在合理時間內應用新的配置值。
 */
public class DynamicConfigurationResponsivenessPropertyTest {
    
    private Path configHotReloadScript;
    private Path tempConfigDir;
    
    @BeforeEach
    void setUp() throws IOException {
        // 設定配置熱重載腳本路徑
        configHotReloadScript = Path.of("otel-agent/config-management/config-hot-reload.sh").toAbsolutePath();
        
        // 創建臨時配置目錄
        tempConfigDir = Files.createTempDirectory("otel-config-test");
        
        // 確保腳本存在且可執行
        if (!Files.exists(configHotReloadScript)) {
            throw new RuntimeException("配置熱重載腳本不存在: " + configHotReloadScript);
        }
    }

    /**
     * 屬性: 配置更新應該在合理時間內生效
     */
    @Property
    @Report(Reporting.GENERATED)
    void configurationUpdatesShouldTakeEffectWithinReasonableTime(
            @ForAll("validConfigUpdates") Map<String, String> configUpdates) {
        
        try {
            // 創建初始配置檔案
            Path configFile = createInitialConfigFile();
            
            // 記錄開始時間
            long startTime = System.currentTimeMillis();
            
            // 應用配置更新
            for (Map.Entry<String, String> update : configUpdates.entrySet()) {
                ProcessResult updateResult = executeScript(
                    configHotReloadScript.toString(),
                    "update",
                    update.getKey(),
                    update.getValue(),
                    configFile.toString()
                );
                
                // 驗證更新成功
                Assertions.assertEquals(0, updateResult.exitCode, 
                    "配置更新應該成功: " + update.getKey() + "=" + update.getValue());
            }
            
            // 計算響應時間
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 驗證響應時間在合理範圍內（5秒內）
            Assertions.assertTrue(responseTime < 5000, 
                "配置更新響應時間應該在 5 秒內，實際: " + responseTime + "ms");
            
            // 驗證配置檔案包含更新的值
            String configContent = Files.readString(configFile);
            for (Map.Entry<String, String> update : configUpdates.entrySet()) {
                Assertions.assertTrue(configContent.contains(update.getKey() + "=" + update.getValue()),
                    "配置檔案應該包含更新的值: " + update.getKey() + "=" + update.getValue());
            }
            
        } catch (Exception e) {
            throw new RuntimeException("動態配置響應性測試失敗", e);
        }
    }

    /**
     * 屬性: 取樣率動態調整應該立即生效
     */
    @Property
    @Report(Reporting.GENERATED)
    void samplingRateAdjustmentShouldTakeEffectImmediately(
            @ForAll("validSamplingRates") String samplingRate) {
        
        try {
            // 創建配置檔案
            Path configFile = createInitialConfigFile();
            
            // 更新取樣率
            ProcessResult updateResult = executeScript(
                configHotReloadScript.toString(),
                "update",
                "OTEL_TRACES_SAMPLER_ARG",
                samplingRate,
                configFile.toString()
            );
            
            // 驗證更新成功
            Assertions.assertEquals(0, updateResult.exitCode, 
                "取樣率更新應該成功");
            
            // 驗證配置檔案包含新的取樣率
            String configContent = Files.readString(configFile);
            Assertions.assertTrue(configContent.contains("OTEL_TRACES_SAMPLER_ARG=" + samplingRate),
                "配置檔案應該包含新的取樣率: " + samplingRate);
            
            // 驗證配置重載
            ProcessResult reloadResult = executeScript(
                configHotReloadScript.toString(),
                "reload",
                configFile.toString()
            );
            
            Assertions.assertEquals(0, reloadResult.exitCode, 
                "配置重載應該成功");
            
        } catch (Exception e) {
            throw new RuntimeException("取樣率動態調整測試失敗", e);
        }
    }

    /**
     * 屬性: 導出頻率動態調整應該立即生效
     */
    @Property
    @Report(Reporting.GENERATED)
    void exportIntervalAdjustmentShouldTakeEffectImmediately(
            @ForAll("validExportIntervals") String exportInterval) {
        
        try {
            // 創建配置檔案
            Path configFile = createInitialConfigFile();
            
            // 更新導出間隔
            ProcessResult updateResult = executeScript(
                configHotReloadScript.toString(),
                "update",
                "OTEL_METRIC_EXPORT_INTERVAL",
                exportInterval,
                configFile.toString()
            );
            
            // 驗證更新成功
            Assertions.assertEquals(0, updateResult.exitCode, 
                "導出間隔更新應該成功");
            
            // 驗證配置檔案包含新的導出間隔
            String configContent = Files.readString(configFile);
            Assertions.assertTrue(configContent.contains("OTEL_METRIC_EXPORT_INTERVAL=" + exportInterval),
                "配置檔案應該包含新的導出間隔: " + exportInterval);
            
        } catch (Exception e) {
            throw new RuntimeException("導出頻率動態調整測試失敗", e);
        }
    }

    /**
     * 屬性: 配置熱重載測試應該總是成功
     */
    @Property
    @Report(Reporting.GENERATED)
    void configurationHotReloadTestShouldAlwaysSucceed() {
        try {
            // 執行熱重載測試
            ProcessResult testResult = executeScript(
                configHotReloadScript.toString(),
                "test-reload"
            );
            
            // 測試應該總是成功
            Assertions.assertEquals(0, testResult.exitCode, 
                "配置熱重載測試應該成功");
            
            // 應該包含成功訊息
            Assertions.assertTrue(testResult.stderr.contains("所有測試通過"),
                "應該包含測試成功訊息");
            
        } catch (Exception e) {
            throw new RuntimeException("配置熱重載測試失敗", e);
        }
    }

    /**
     * 屬性: 無效配置更新應該被拒絕
     */
    @Property
    @Report(Reporting.GENERATED)
    void invalidConfigurationUpdatesShouldBeRejected(
            @ForAll("invalidConfigUpdates") Map<String, String> invalidUpdates) {
        
        try {
            // 創建配置檔案
            Path configFile = createInitialConfigFile();
            
            // 嘗試應用無效配置更新
            for (Map.Entry<String, String> update : invalidUpdates.entrySet()) {
                ProcessResult updateResult = executeScript(
                    configHotReloadScript.toString(),
                    "update",
                    update.getKey(),
                    update.getValue(),
                    configFile.toString()
                );
                
                // 無效更新應該失敗
                Assertions.assertNotEquals(0, updateResult.exitCode, 
                    "無效配置更新應該被拒絕: " + update.getKey() + "=" + update.getValue());
            }
            
        } catch (Exception e) {
            throw new RuntimeException("無效配置更新測試失敗", e);
        }
    }

    // 生成器：有效配置更新
    @Provide
    Arbitrary<Map<String, String>> validConfigUpdates() {
        return Arbitraries.maps(
            Arbitraries.of(
                "OTEL_LOG_LEVEL",
                "OTEL_JAVAAGENT_DEBUG",
                "OTEL_TRACES_SAMPLER_ARG"
            ),
            Arbitraries.oneOf(
                Arbitraries.of("DEBUG", "INFO", "WARN", "ERROR"),
                Arbitraries.of("true", "false"),
                Arbitraries.doubles().between(0.0, 1.0).map(String::valueOf)
            )
        ).ofMinSize(1).ofMaxSize(2);
    }

    // 生成器：有效取樣率
    @Provide
    Arbitrary<String> validSamplingRates() {
        return Arbitraries.doubles()
            .between(0.0, 1.0)
            .map(rate -> String.format("%.2f", rate));
    }

    // 生成器：有效導出間隔
    @Provide
    Arbitrary<String> validExportIntervals() {
        return Arbitraries.integers()
            .between(5000, 120000)  // 5秒到2分鐘
            .map(String::valueOf);
    }

    // 生成器：無效配置更新
    @Provide
    Arbitrary<Map<String, String>> invalidConfigUpdates() {
        return Arbitraries.maps(
            Arbitraries.of(
                "OTEL_TRACES_SAMPLER_ARG",
                "OTEL_LOG_LEVEL",
                "OTEL_EXPORTER_OTLP_ENDPOINT"
            ),
            Arbitraries.oneOf(
                Arbitraries.of("2.0", "-1", "invalid"),
                Arbitraries.of("INVALID_LEVEL", "wrong"),
                Arbitraries.of("not-a-url", "invalid://endpoint")
            )
        ).ofMinSize(1).ofMaxSize(2);
    }

    // 輔助方法：創建初始配置檔案
    private Path createInitialConfigFile() throws IOException {
        Path configFile = tempConfigDir.resolve("test-config-" + System.currentTimeMillis() + ".env");
        List<String> lines = List.of(
            "# 測試配置檔案",
            "OTEL_SERVICE_NAME=test-service",
            "OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317",
            "OTEL_TRACES_SAMPLER_ARG=0.1",
            "OTEL_LOG_LEVEL=INFO",
            "OTEL_JAVAAGENT_DEBUG=false",
            "OTEL_METRIC_EXPORT_INTERVAL=30000"
        );
        
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
        
        // 等待進程完成，最多等待 10 秒
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("腳本執行超時");
        }
        
        int exitCode = process.exitValue();
        
        return new ProcessResult(exitCode, stdout, stderr);
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