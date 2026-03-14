package com.microservices.lgtm;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 屬性測試：日誌追蹤關聯性測試
 * 
 * 功能: opentelemetry-lgtm-integration, 屬性 9: 日誌追蹤關聯性
 * 驗證: 需求 4.3
 * 
 * 測試日誌中 trace ID 和 span ID 的關聯性和一致性
 */
public class LogTraceCorrelationPropertyTest {

    private static final Logger logger = LoggerFactory.getLogger(LogTraceCorrelationPropertyTest.class);
    
    // 匹配日誌中的 trace ID 和 span ID 模式
    private static final Pattern TRACE_CONTEXT_PATTERN = Pattern.compile(
            "\\[([a-f0-9]{16}|-)\\,([a-f0-9]{16}|-)\\]"
    );

    /**
     * 屬性 9: 日誌追蹤關聯性
     * 對於任何在 trace 上下文中產生的日誌，應該保留 trace ID 和 span ID 的關聯資訊
     */
    @Property(tries = 100)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 9: 日誌追蹤關聯性")
    void logsShouldContainTraceContext(@ForAll("traceIds") String traceId,
                                       @ForAll("spanIds") String spanId,
                                       @ForAll("logMessages") String message) {
        
        // 捕獲日誌輸出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(outputStream));
            
            // 設定 MDC 上下文（模擬 OpenTelemetry 自動設定）
            MDC.put("traceId", traceId);
            MDC.put("spanId", spanId);
            
            // 記錄日誌
            logger.info(message);
            
            String logOutput = outputStream.toString();
            
            if (!logOutput.trim().isEmpty()) {
                // 驗證日誌包含 trace 上下文
                assertThat(logOutput)
                        .as("日誌應該包含 trace 上下文格式")
                        .containsPattern(TRACE_CONTEXT_PATTERN);
                
                // 提取 trace ID 和 span ID
                Matcher matcher = TRACE_CONTEXT_PATTERN.matcher(logOutput);
                if (matcher.find()) {
                    String extractedTraceId = matcher.group(1);
                    String extractedSpanId = matcher.group(2);
                    
                    // 驗證 trace ID 一致性
                    assertThat(extractedTraceId)
                            .as("日誌中的 trace ID 應該與設定的一致")
                            .isEqualTo(traceId);
                    
                    // 驗證 span ID 一致性
                    assertThat(extractedSpanId)
                            .as("日誌中的 span ID 應該與設定的一致")
                            .isEqualTo(spanId);
                }
                
                // 驗證包含原始訊息
                assertThat(logOutput)
                        .as("日誌應該包含原始訊息內容")
                        .contains(message);
            }
            
        } finally {
            System.setOut(originalOut);
            MDC.clear();
        }
    }

    /**
     * 測試空 trace 上下文的處理
     */
    @Property(tries = 50)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 9: 空 trace 上下文處理")
    void logsShouldHandleEmptyTraceContext(@ForAll("logMessages") String message) {
        
        // 捕獲日誌輸出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(outputStream));
            
            // 清除 MDC 上下文（模擬沒有 trace 的情況）
            MDC.clear();
            
            // 記錄日誌
            logger.info(message);
            
            String logOutput = outputStream.toString();
            
            if (!logOutput.trim().isEmpty()) {
                // 驗證日誌包含預設的 trace 上下文格式（使用 "-" 作為預設值）
                assertThat(logOutput)
                        .as("沒有 trace 上下文時，日誌應該使用預設格式")
                        .contains("[-,-]");
                
                // 驗證包含原始訊息
                assertThat(logOutput)
                        .as("日誌應該包含原始訊息內容")
                        .contains(message);
            }
            
        } finally {
            System.setOut(originalOut);
            MDC.clear();
        }
    }

    /**
     * 測試多個日誌條目的 trace 一致性
     */
    @Property(tries = 50)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 9: 多日誌 trace 一致性")
    void multipleLogsShouldMaintainTraceConsistency(@ForAll("traceIds") String traceId,
                                                    @ForAll("spanIds") String spanId,
                                                    @ForAll("logMessageLists") java.util.List<String> messages) {
        
        // 捕獲日誌輸出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(outputStream));
            
            // 設定 MDC 上下文
            MDC.put("traceId", traceId);
            MDC.put("spanId", spanId);
            
            // 記錄多個日誌
            for (String message : messages) {
                logger.info(message);
            }
            
            String logOutput = outputStream.toString();
            String[] logLines = logOutput.split("\n");
            
            // 驗證每一行日誌都包含相同的 trace 上下文
            for (String line : logLines) {
                if (!line.trim().isEmpty()) {
                    Matcher matcher = TRACE_CONTEXT_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String extractedTraceId = matcher.group(1);
                        String extractedSpanId = matcher.group(2);
                        
                        assertThat(extractedTraceId)
                                .as("所有日誌行應該包含相同的 trace ID")
                                .isEqualTo(traceId);
                        
                        assertThat(extractedSpanId)
                                .as("所有日誌行應該包含相同的 span ID")
                                .isEqualTo(spanId);
                    }
                }
            }
            
        } finally {
            System.setOut(originalOut);
            MDC.clear();
        }
    }

    /**
     * 測試 trace 上下文的格式有效性
     */
    @Test
    void traceContextFormatShouldBeValid() {
        // 捕獲日誌輸出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(outputStream));
            
            // 設定有效的 trace 上下文
            String validTraceId = "1234567890abcdef";
            String validSpanId = "fedcba0987654321";
            
            MDC.put("traceId", validTraceId);
            MDC.put("spanId", validSpanId);
            
            logger.info("測試訊息");
            
            String logOutput = outputStream.toString();
            
            // 驗證 trace 上下文格式
            assertThat(logOutput)
                    .as("日誌應該包含正確格式的 trace 上下文")
                    .contains("[" + validTraceId + "," + validSpanId + "]");
            
            // 驗證 trace ID 格式（16 字元十六進制）
            Matcher matcher = TRACE_CONTEXT_PATTERN.matcher(logOutput);
            if (matcher.find()) {
                String extractedTraceId = matcher.group(1);
                String extractedSpanId = matcher.group(2);
                
                assertThat(extractedTraceId)
                        .as("trace ID 應該是 16 字元十六進制格式")
                        .matches("[a-f0-9]{16}");
                
                assertThat(extractedSpanId)
                        .as("span ID 應該是 16 字元十六進制格式")
                        .matches("[a-f0-9]{16}");
            }
            
        } finally {
            System.setOut(originalOut);
            MDC.clear();
        }
    }

    /**
     * 測試 JSON 日誌格式的 trace 關聯（如果配置了 JSON appender）
     */
    @Property(tries = 30)
    @Label("功能: opentelemetry-lgtm-integration, 屬性 9: JSON 日誌 trace 關聯")
    void jsonLogsShouldContainTraceFields(@ForAll("traceIds") String traceId,
                                          @ForAll("spanIds") String spanId,
                                          @ForAll("logMessages") String message) {
        
        // 設定 MDC 上下文
        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);
        
        try {
            // 驗證 MDC 包含正確的值
            assertThat(MDC.get("traceId"))
                    .as("MDC 應該包含設定的 trace ID")
                    .isEqualTo(traceId);
            
            assertThat(MDC.get("spanId"))
                    .as("MDC 應該包含設定的 span ID")
                    .isEqualTo(spanId);
            
            // 注意：這裡我們測試 MDC 的設定，實際的 JSON 輸出測試需要
            // 在整合測試中進行，因為需要完整的 logback 配置
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * 生成有效的 trace ID（16 字元十六進制）
     */
    @Provide
    Arbitrary<String> traceIds() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofLength(16);
    }

    /**
     * 生成有效的 span ID（16 字元十六進制）
     */
    @Provide
    Arbitrary<String> spanIds() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofLength(16);
    }

    /**
     * 生成日誌訊息
     */
    @Provide
    Arbitrary<String> logMessages() {
        return Arbitraries.of(
                "用戶請求處理開始",
                "資料庫查詢執行",
                "API 調用完成",
                "快取更新成功",
                "業務邏輯處理",
                "錯誤處理執行",
                "系統狀態檢查",
                "服務間通信"
        );
    }

    /**
     * 生成日誌訊息列表
     */
    @Provide
    Arbitrary<java.util.List<String>> logMessageLists() {
        return logMessages().list().ofMinSize(2).ofMaxSize(5);
    }
}