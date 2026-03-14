package com.microservices.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 日誌級別管理控制器
 * 提供動態調整日誌級別的功能
 */
@RestController
@RequestMapping("/api/logging")
@Tag(name = "日誌管理", description = "動態日誌級別管理 API")
public class LoggingController {

    private static final Logger logger = LoggerFactory.getLogger(LoggingController.class);
    private final LoggingSystem loggingSystem;

    public LoggingController(LoggingSystem loggingSystem) {
        this.loggingSystem = loggingSystem;
    }

    /**
     * 獲取當前日誌級別配置
     */
    @GetMapping("/levels")
    @Operation(summary = "獲取日誌級別", description = "獲取當前所有 logger 的日誌級別配置")
    public ResponseEntity<Map<String, String>> getLogLevels() {
        Map<String, String> logLevels = new HashMap<>();
        
        // 獲取主要 logger 的級別
        String[] loggerNames = {
            "ROOT",
            "com.microservices.product",
            "org.springframework",
            "org.hibernate",
            "io.opentelemetry",
            "io.micrometer"
        };
        
        for (String loggerName : loggerNames) {
            LogLevel level = loggingSystem.getLoggerConfiguration(loggerName) != null 
                ? loggingSystem.getLoggerConfiguration(loggerName).getEffectiveLevel()
                : null;
            logLevels.put(loggerName, level != null ? level.name() : "INHERITED");
        }
        
        logger.info("獲取日誌級別配置: {}", logLevels);
        return ResponseEntity.ok(logLevels);
    }

    /**
     * 設定特定 logger 的日誌級別
     */
    @PostMapping("/levels/{loggerName}")
    @Operation(summary = "設定日誌級別", description = "動態設定指定 logger 的日誌級別")
    public ResponseEntity<Map<String, String>> setLogLevel(
            @Parameter(description = "Logger 名稱", example = "com.microservices.product")
            @PathVariable String loggerName,
            @Parameter(description = "日誌級別", example = "DEBUG")
            @RequestParam LogLevel level) {
        
        try {
            // 設定日誌級別
            loggingSystem.setLogLevel(loggerName, level);
            
            // 驗證設定是否成功
            LogLevel currentLevel = loggingSystem.getLoggerConfiguration(loggerName) != null
                ? loggingSystem.getLoggerConfiguration(loggerName).getEffectiveLevel()
                : null;
            
            Map<String, String> result = new HashMap<>();
            result.put("logger", loggerName);
            result.put("previousLevel", "UNKNOWN");
            result.put("newLevel", currentLevel != null ? currentLevel.name() : "INHERITED");
            result.put("status", "SUCCESS");
            
            logger.info("日誌級別已更新: {} -> {}", loggerName, level);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("設定日誌級別失敗: {} -> {}", loggerName, level, e);
            
            Map<String, String> error = new HashMap<>();
            error.put("logger", loggerName);
            error.put("requestedLevel", level.name());
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 重置日誌級別到預設配置
     */
    @PostMapping("/levels/reset")
    @Operation(summary = "重置日誌級別", description = "將所有日誌級別重置為預設配置")
    public ResponseEntity<Map<String, String>> resetLogLevels() {
        try {
            // 重置主要 logger 到預設級別
            loggingSystem.setLogLevel("com.microservices.product", LogLevel.INFO);
            loggingSystem.setLogLevel("org.springframework", LogLevel.WARN);
            loggingSystem.setLogLevel("org.hibernate", LogLevel.WARN);
            loggingSystem.setLogLevel("io.opentelemetry", LogLevel.INFO);
            loggingSystem.setLogLevel("io.micrometer", LogLevel.INFO);
            
            Map<String, String> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "日誌級別已重置為預設配置");
            
            logger.info("日誌級別已重置為預設配置");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("重置日誌級別失敗", e);
            
            Map<String, String> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 測試日誌輸出
     */
    @PostMapping("/test")
    @Operation(summary = "測試日誌輸出", description = "產生不同級別的測試日誌")
    public ResponseEntity<Map<String, String>> testLogging(
            @Parameter(description = "測試訊息", example = "測試日誌訊息")
            @RequestParam(defaultValue = "測試日誌訊息") String message) {
        
        // 產生不同級別的日誌
        logger.error("ERROR 級別測試: {}", message);
        logger.warn("WARN 級別測試: {}", message);
        logger.info("INFO 級別測試: {}", message);
        logger.debug("DEBUG 級別測試: {}", message);
        logger.trace("TRACE 級別測試: {}", message);
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("message", "已產生所有級別的測試日誌");
        result.put("testMessage", message);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 獲取日誌級別說明
     */
    @GetMapping("/levels/info")
    @Operation(summary = "日誌級別說明", description = "獲取各日誌級別的說明和用途")
    public ResponseEntity<Map<String, Object>> getLogLevelInfo() {
        Map<String, Object> info = new HashMap<>();
        
        Map<String, String> levels = new HashMap<>();
        levels.put("ERROR", "錯誤級別 - 只記錄錯誤訊息");
        levels.put("WARN", "警告級別 - 記錄警告和錯誤訊息");
        levels.put("INFO", "資訊級別 - 記錄一般資訊、警告和錯誤訊息");
        levels.put("DEBUG", "除錯級別 - 記錄除錯、資訊、警告和錯誤訊息");
        levels.put("TRACE", "追蹤級別 - 記錄所有級別的訊息");
        
        Map<String, String> recommendations = new HashMap<>();
        recommendations.put("生產環境", "INFO 或 WARN");
        recommendations.put("測試環境", "DEBUG");
        recommendations.put("開發環境", "DEBUG 或 TRACE");
        recommendations.put("故障排除", "DEBUG 或 TRACE");
        
        info.put("levels", levels);
        info.put("recommendations", recommendations);
        info.put("currentEnvironment", getCurrentEnvironment());
        
        return ResponseEntity.ok(info);
    }

    /**
     * 獲取當前環境
     */
    private String getCurrentEnvironment() {
        String profile = System.getProperty("spring.profiles.active");
        if (profile == null || profile.isEmpty()) {
            profile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        return profile != null ? profile : "default";
    }
}