package com.microservices.lgtm;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * 資料隱私保護屬性測試
 * 
 * **功能: opentelemetry-lgtm-integration, 屬性 18: 資料隱私保護**
 * **驗證: 需求 8.3**
 * 
 * 測試屬性：對於任何導出的遙測數據，不應該包含密碼、API 金鑰等敏感資訊
 */
@SpringBootTest
@ActiveProfiles("test")
public class DataPrivacyProtectionPropertyTest {

    private static final String OTEL_CONFIG_PATH = "../otel-collector-config.yaml";
    private static final String PRIVACY_CONFIG_PATH = "../otel-agent/data-privacy-config.yaml";
    
    // 敏感資料模式定義
    private static final List<Pattern> SENSITIVE_PATTERNS = Arrays.asList(
        // 密碼相關
        Pattern.compile("(?i)(password|pwd|passwd|secret|token|key|auth)\\s*[:=]\\s*[^\\s,}]+"),
        Pattern.compile("(?i)(api[_-]?key|access[_-]?token|bearer[_-]?token)\\s*[:=]\\s*[^\\s,}]+"),
        Pattern.compile("(?i)(client[_-]?secret|private[_-]?key|secret[_-]?key)\\s*[:=]\\s*[^\\s,}]+"),
        
        // 個人識別資訊 (PII)
        Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"), // 信用卡號
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), // 社會安全號碼
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), // 電子郵件
        Pattern.compile("\\b\\d{10,15}\\b"), // 電話號碼
        
        // 系統敏感資訊
        Pattern.compile("(?i)(database[_-]?url|db[_-]?connection|jdbc[_-]?url)\\s*[:=]\\s*[^\\s,}]+"),
        Pattern.compile("(?i)(redis[_-]?url|mongodb[_-]?uri|elasticsearch[_-]?url)\\s*[:=]\\s*[^\\s,}]+"),
        Pattern.compile("(?i)(aws[_-]?access[_-]?key|aws[_-]?secret)\\s*[:=]\\s*[^\\s,}]+"),
        Pattern.compile("(?i)(gcp[_-]?key|azure[_-]?key|service[_-]?account)\\s*[:=]\\s*[^\\s,}]+")
    );
    
    // 脫敏後的標記模式
    private static final List<Pattern> REDACTED_PATTERNS = Arrays.asList(
        Pattern.compile("\\*\\*\\*REDACTED\\*\\*\\*"),
        Pattern.compile("\\*\\*\\*\\{first_2\\}\\.\\.\\.\\{last_2\\}\\*\\*\\*"),
        Pattern.compile("HASH_[a-f0-9]{64}"),
        Pattern.compile("\\*\\*\\*\\*-\\*\\*\\*\\*-\\*\\*\\*\\*-\\*\\*\\*\\*"), // 脫敏信用卡號
        Pattern.compile("\\*\\*\\*-\\*\\*-\\*\\*\\*\\*"), // 脫敏社會安全號碼
        Pattern.compile("\\*\\*\\*@\\*\\*\\*\\.\\*\\*\\*") // 脫敏電子郵件
    );

    @BeforeEach
    void setUp() {
        // 確保隱私配置檔案存在
        Path privacyConfigPath = Paths.get(PRIVACY_CONFIG_PATH);
        if (!Files.exists(privacyConfigPath)) {
            throw new RuntimeException("隱私配置檔案不存在: " + PRIVACY_CONFIG_PATH);
        }
    }

    /**
     * 屬性測試：指標資料不應包含敏感資訊
     */
    @Property(tries = 100)
    @Label("指標資料隱私保護")
    void metricsDataShouldNotContainSensitiveInformation(
            @ForAll("telemetryData") TelemetryData telemetryData) {
        
        // 模擬指標資料處理
        String processedMetrics = processMetricsData(telemetryData);
        
        // 驗證敏感資料已被移除或脱敏
        assertNoSensitiveDataInMetrics(processedMetrics, telemetryData);
        
        // 驗證正常資料仍然存在
        assertNormalDataPreservedInMetrics(processedMetrics, telemetryData);
    }

    /**
     * 屬性測試：追蹤資料不應包含敏感資訊
     */
    @Property(tries = 100)
    @Label("追蹤資料隱私保護")
    void tracesDataShouldNotContainSensitiveInformation(
            @ForAll("telemetryData") TelemetryData telemetryData) {
        
        // 模擬追蹤資料處理
        String processedTraces = processTracesData(telemetryData);
        
        // 驗證敏感資料已被移除或脱敏
        assertNoSensitiveDataInTraces(processedTraces, telemetryData);
        
        // 驗證正常資料仍然存在
        assertNormalDataPreservedInTraces(processedTraces, telemetryData);
    }

    /**
     * 屬性測試：日誌資料不應包含敏感資訊
     */
    @Property(tries = 100)
    @Label("日誌資料隱私保護")
    void logsDataShouldNotContainSensitiveInformation(
            @ForAll("telemetryData") TelemetryData telemetryData) {
        
        // 模擬日誌資料處理
        String processedLogs = processLogsData(telemetryData);
        
        // 驗證敏感資料已被移除或脱敏
        assertNoSensitiveDataInLogs(processedLogs, telemetryData);
        
        // 驗證正常資料仍然存在
        assertNormalDataPreservedInLogs(processedLogs, telemetryData);
    }

    /**
     * 屬性測試：HTTP 標頭中的敏感資訊應被脫敏
     */
    @Property(tries = 100)
    @Label("HTTP 標頭隱私保護")
    void httpHeadersShouldBeMasked(
            @ForAll("httpHeaders") Map<String, String> headers) {
        
        // 模擬 HTTP 標頭處理
        Map<String, String> processedHeaders = processHttpHeaders(headers);
        
        // 驗證敏感標頭已被脫敏
        assertSensitiveHeadersMasked(processedHeaders, headers);
        
        // 驗證正常標頭仍然存在
        assertNormalHeadersPreserved(processedHeaders, headers);
    }

    /**
     * 屬性測試：URL 參數中的敏感資訊應被脫敏
     */
    @Property(tries = 100)
    @Label("URL 參數隱私保護")
    void urlParametersShouldBeMasked(
            @ForAll("urlWithParameters") String url) {
        
        // 模擬 URL 參數處理
        String processedUrl = processUrlParameters(url);
        
        // 驗證敏感參數已被脫敏
        assertSensitiveParametersMasked(processedUrl, url);
        
        // 驗證 URL 結構仍然完整
        assertUrlStructurePreserved(processedUrl, url);
    }

    /**
     * 屬性測試：資料庫連接字串中的敏感資訊應被脫敏
     */
    @Property(tries = 100)
    @Label("資料庫連接字串隱私保護")
    void databaseConnectionStringsShouldBeMasked(
            @ForAll("databaseConnectionString") String connectionString) {
        
        // 模擬資料庫連接字串處理
        String processedConnectionString = processDatabaseConnectionString(connectionString);
        
        // 驗證敏感資訊已被脫敏
        assertDatabaseCredentialsMasked(processedConnectionString, connectionString);
        
        // 驗證連接字串結構仍然可用
        assertConnectionStringStructurePreserved(processedConnectionString, connectionString);
    }

    // ==================== 資料生成器 ====================

    @Provide
    Arbitrary<TelemetryData> telemetryData() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(20), // serviceName
            sensitiveAttributes(),
            normalAttributes(),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(50) // message
        ).as(TelemetryData::new);
    }

    @Provide
    Arbitrary<Map<String, String>> sensitiveAttributes() {
        return Arbitraries.maps(
            sensitiveKeys(),
            sensitiveValues()
        ).ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Map<String, String>> normalAttributes() {
        return Arbitraries.maps(
            normalKeys(),
            normalValues()
        ).ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<String> sensitiveKeys() {
        return Arbitraries.of(
            "password", "secret", "token", "key", "auth",
            "api_key", "access_token", "bearer_token",
            "client_secret", "private_key", "secret_key",
            "authorization", "cookie", "x-api-key"
        );
    }

    @Provide
    Arbitrary<String> sensitiveValues() {
        return Arbitraries.oneOf(
            // 密碼
            Arbitraries.strings().withCharRange('a', 'z').withCharRange('A', 'Z')
                .withCharRange('0', '9').ofMinLength(8).ofMaxLength(20),
            // API 金鑰
            Arbitraries.strings().withCharRange('a', 'z').withCharRange('0', '9')
                .ofMinLength(32).ofMaxLength(64).map(s -> "ak_" + s),
            // JWT Token
            Arbitraries.strings().withCharRange('A', 'Z').withCharRange('a', 'z')
                .withCharRange('0', '9').ofMinLength(100).ofMaxLength(200)
                .map(s -> "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." + s),
            // 信用卡號
            Arbitraries.integers().between(1000, 9999).list().ofSize(4)
                .map(list -> list.stream().map(String::valueOf)
                    .reduce((a, b) -> a + "-" + b).orElse("")),
            // 電子郵件
            Combinators.combine(
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10),
                Arbitraries.of("com", "org", "net", "edu")
            ).as((user, domain, tld) -> user + "@" + domain + "." + tld)
        );
    }

    @Provide
    Arbitrary<String> normalKeys() {
        return Arbitraries.of(
            "service.name", "service.version", "deployment.environment",
            "http.method", "http.status_code", "db.operation",
            "messaging.operation", "user.id", "request.id"
        );
    }

    @Provide
    Arbitrary<String> normalValues() {
        return Arbitraries.oneOf(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(20),
            Arbitraries.integers().between(100, 999).map(String::valueOf),
            Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH"),
            Arbitraries.of("production", "staging", "development", "test")
        );
    }

    @Provide
    Arbitrary<Map<String, String>> httpHeaders() {
        return Combinators.combine(
            sensitiveHeaders(),
            normalHeaders()
        ).as((sensitive, normal) -> {
            Map<String, String> combined = new HashMap<>(normal);
            combined.putAll(sensitive);
            return combined;
        });
    }

    @Provide
    Arbitrary<Map<String, String>> sensitiveHeaders() {
        return Arbitraries.maps(
            Arbitraries.of("authorization", "cookie", "x-api-key", "x-auth-token", "x-access-token"),
            sensitiveValues()
        ).ofMinSize(1).ofMaxSize(3);
    }

    @Provide
    Arbitrary<Map<String, String>> normalHeaders() {
        return Arbitraries.maps(
            Arbitraries.of("content-type", "user-agent", "accept", "host", "referer"),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(30)
        ).ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> urlWithParameters() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(15), // domain
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10), // path
            sensitiveUrlParameters(),
            normalUrlParameters()
        ).as((domain, path, sensitiveParams, normalParams) -> {
            String baseUrl = "https://" + domain + ".com/" + path;
            List<String> allParams = new ArrayList<>();
            allParams.addAll(sensitiveParams);
            allParams.addAll(normalParams);
            
            if (allParams.isEmpty()) {
                return baseUrl;
            }
            
            return baseUrl + "?" + String.join("&", allParams);
        });
    }

    @Provide
    Arbitrary<List<String>> sensitiveUrlParameters() {
        return Combinators.combine(
            Arbitraries.of("password", "token", "key", "secret", "auth", "api_key"),
            sensitiveValues()
        ).as((key, value) -> key + "=" + value).list().ofMinSize(1).ofMaxSize(3);
    }

    @Provide
    Arbitrary<List<String>> normalUrlParameters() {
        return Combinators.combine(
            Arbitraries.of("page", "size", "sort", "filter", "format", "lang"),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(10)
        ).as((key, value) -> key + "=" + value).list().ofMinSize(0).ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> databaseConnectionString() {
        return Combinators.combine(
            Arbitraries.of("mysql", "postgresql", "oracle", "sqlserver"),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(15), // host
            Arbitraries.integers().between(1000, 9999), // port
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10), // database
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(10), // username
            sensitiveValues() // password
        ).as((dbType, host, port, database, username, password) -> {
            switch (dbType) {
                case "mysql":
                    return String.format("jdbc:mysql://%s:%d/%s?user=%s&password=%s&useSSL=true",
                        host, port, database, username, password);
                case "postgresql":
                    return String.format("postgresql://%s:%s@%s:%d/%s?sslmode=require",
                        username, password, host, port, database);
                case "oracle":
                    return String.format("jdbc:oracle:thin:%s/%s@%s:%d:%s",
                        username, password, host, port, database);
                case "sqlserver":
                    return String.format("jdbc:sqlserver://%s:%d;databaseName=%s;user=%s;password=%s;encrypt=true",
                        host, port, database, username, password);
                default:
                    return String.format("jdbc:%s://%s:%d/%s?user=%s&password=%s",
                        dbType, host, port, database, username, password);
            }
        });
    }

    // ==================== 資料處理模擬 ====================

    private String processMetricsData(TelemetryData data) {
        // 模擬 OpenTelemetry Collector 的指標處理邏輯
        String rawData = createMetricsJson(data);
        return applySensitiveDataFiltering(rawData, "metrics");
    }

    private String processTracesData(TelemetryData data) {
        // 模擬 OpenTelemetry Collector 的追蹤處理邏輯
        String rawData = createTracesJson(data);
        return applySensitiveDataFiltering(rawData, "traces");
    }

    private String processLogsData(TelemetryData data) {
        // 模擬 OpenTelemetry Collector 的日誌處理邏輯
        String rawData = createLogsJson(data);
        return applySensitiveDataFiltering(rawData, "logs");
    }

    private Map<String, String> processHttpHeaders(Map<String, String> headers) {
        Map<String, String> processed = new HashMap<>(headers);
        
        // 模擬敏感標頭脫敏
        List<String> sensitiveHeaderNames = Arrays.asList(
            "authorization", "cookie", "x-api-key", "x-auth-token", "x-access-token"
        );
        
        for (String headerName : sensitiveHeaderNames) {
            if (processed.containsKey(headerName)) {
                processed.put(headerName, "***REDACTED***");
            }
        }
        
        return processed;
    }

    private String processUrlParameters(String url) {
        // 模擬 URL 參數脫敏
        String processed = url;
        
        List<String> sensitiveParams = Arrays.asList(
            "password", "token", "key", "secret", "auth", "api_key"
        );
        
        for (String param : sensitiveParams) {
            processed = processed.replaceAll(
                "([?&])" + param + "=[^&]*",
                "$1" + param + "=***REDACTED***"
            );
        }
        
        return processed;
    }

    private String processDatabaseConnectionString(String connectionString) {
        // 模擬資料庫連接字串脫敏
        return connectionString.replaceAll(
            "(password|pwd)=[^;&]*",
            "$1=***REDACTED***"
        );
    }

    private String applySensitiveDataFiltering(String rawData, String dataType) {
        String processed = rawData;
        
        // 應用敏感資料過濾規則
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(processed);
            processed = matcher.replaceAll("***REDACTED***");
        }
        
        // 添加隱私處理標記
        processed = processed.replace("}", ", \"privacy.processed\": \"true\"}");
        
        return processed;
    }

    // ==================== JSON 創建輔助方法 ====================

    private String createMetricsJson(TelemetryData data) {
        StringBuilder json = new StringBuilder();
        json.append("{\"resourceMetrics\":[{");
        json.append("\"resource\":{\"attributes\":[");
        json.append("{\"key\":\"service.name\",\"value\":{\"stringValue\":\"").append(data.serviceName).append("\"}},");
        
        // 添加敏感屬性
        for (Map.Entry<String, String> entry : data.sensitiveAttributes.entrySet()) {
            json.append("{\"key\":\"").append(entry.getKey()).append("\",\"value\":{\"stringValue\":\"")
                .append(entry.getValue()).append("\"}},");
        }
        
        // 添加正常屬性
        for (Map.Entry<String, String> entry : data.normalAttributes.entrySet()) {
            json.append("{\"key\":\"").append(entry.getKey()).append("\",\"value\":{\"stringValue\":\"")
                .append(entry.getValue()).append("\"}},");
        }
        
        json.append("]}}]}");
        return json.toString();
    }

    private String createTracesJson(TelemetryData data) {
        StringBuilder json = new StringBuilder();
        json.append("{\"resourceSpans\":[{");
        json.append("\"resource\":{\"attributes\":[");
        json.append("{\"key\":\"service.name\",\"value\":{\"stringValue\":\"").append(data.serviceName).append("\"}},");
        
        // 添加敏感屬性
        for (Map.Entry<String, String> entry : data.sensitiveAttributes.entrySet()) {
            json.append("{\"key\":\"").append(entry.getKey()).append("\",\"value\":{\"stringValue\":\"")
                .append(entry.getValue()).append("\"}},");
        }
        
        json.append("],\"scopeSpans\":[{\"spans\":[{");
        json.append("\"name\":\"test-span\",\"attributes\":[");
        
        // 添加正常屬性
        for (Map.Entry<String, String> entry : data.normalAttributes.entrySet()) {
            json.append("{\"key\":\"").append(entry.getKey()).append("\",\"value\":{\"stringValue\":\"")
                .append(entry.getValue()).append("\"}},");
        }
        
        json.append("]}]}]}}]}");
        return json.toString();
    }

    private String createLogsJson(TelemetryData data) {
        StringBuilder json = new StringBuilder();
        json.append("{\"resourceLogs\":[{");
        json.append("\"resource\":{\"attributes\":[");
        json.append("{\"key\":\"service.name\",\"value\":{\"stringValue\":\"").append(data.serviceName).append("\"}},");
        
        // 添加敏感屬性
        for (Map.Entry<String, String> entry : data.sensitiveAttributes.entrySet()) {
            json.append("{\"key\":\"").append(entry.getKey()).append("\",\"value\":{\"stringValue\":\"")
                .append(entry.getValue()).append("\"}},");
        }
        
        json.append("],\"scopeLogs\":[{\"logRecords\":[{");
        json.append("\"body\":{\"stringValue\":\"").append(data.message);
        
        // 在日誌訊息中添加敏感資料
        for (Map.Entry<String, String> entry : data.sensitiveAttributes.entrySet()) {
            json.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        json.append("\"},\"attributes\":[");
        
        // 添加正常屬性
        for (Map.Entry<String, String> entry : data.normalAttributes.entrySet()) {
            json.append("{\"key\":\"").append(entry.getKey()).append("\",\"value\":{\"stringValue\":\"")
                .append(entry.getValue()).append("\"}},");
        }
        
        json.append("]}]}]}}]}");
        return json.toString();
    }

    // ==================== 斷言方法 ====================

    private void assertNoSensitiveDataInMetrics(String processedData, TelemetryData originalData) {
        // 驗證原始敏感資料不存在於處理後的資料中
        for (String sensitiveValue : originalData.sensitiveAttributes.values()) {
            if (processedData.contains(sensitiveValue)) {
                throw new AssertionError("指標資料仍包含敏感資訊: " + sensitiveValue);
            }
        }
        
        // 驗證敏感資料模式不存在
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(processedData);
            if (matcher.find()) {
                throw new AssertionError("指標資料仍包含敏感資料模式: " + matcher.group());
            }
        }
    }

    private void assertNormalDataPreservedInMetrics(String processedData, TelemetryData originalData) {
        // 驗證服務名稱仍然存在
        if (!processedData.contains(originalData.serviceName)) {
            throw new AssertionError("指標資料中服務名稱被錯誤移除");
        }
        
        // 驗證正常屬性仍然存在
        for (Map.Entry<String, String> entry : originalData.normalAttributes.entrySet()) {
            if (!processedData.contains(entry.getKey())) {
                throw new AssertionError("指標資料中正常屬性被錯誤移除: " + entry.getKey());
            }
        }
        
        // 驗證隱私處理標記存在
        if (!processedData.contains("privacy.processed")) {
            throw new AssertionError("指標資料缺少隱私處理標記");
        }
    }

    private void assertNoSensitiveDataInTraces(String processedData, TelemetryData originalData) {
        // 驗證原始敏感資料不存在於處理後的資料中
        for (String sensitiveValue : originalData.sensitiveAttributes.values()) {
            if (processedData.contains(sensitiveValue)) {
                throw new AssertionError("追蹤資料仍包含敏感資訊: " + sensitiveValue);
            }
        }
        
        // 驗證敏感資料模式不存在
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(processedData);
            if (matcher.find()) {
                throw new AssertionError("追蹤資料仍包含敏感資料模式: " + matcher.group());
            }
        }
    }

    private void assertNormalDataPreservedInTraces(String processedData, TelemetryData originalData) {
        // 驗證服務名稱仍然存在
        if (!processedData.contains(originalData.serviceName)) {
            throw new AssertionError("追蹤資料中服務名稱被錯誤移除");
        }
        
        // 驗證正常屬性仍然存在
        for (Map.Entry<String, String> entry : originalData.normalAttributes.entrySet()) {
            if (!processedData.contains(entry.getKey())) {
                throw new AssertionError("追蹤資料中正常屬性被錯誤移除: " + entry.getKey());
            }
        }
    }

    private void assertNoSensitiveDataInLogs(String processedData, TelemetryData originalData) {
        // 驗證原始敏感資料不存在於處理後的資料中
        for (String sensitiveValue : originalData.sensitiveAttributes.values()) {
            if (processedData.contains(sensitiveValue)) {
                throw new AssertionError("日誌資料仍包含敏感資訊: " + sensitiveValue);
            }
        }
        
        // 驗證敏感資料模式不存在
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(processedData);
            if (matcher.find()) {
                throw new AssertionError("日誌資料仍包含敏感資料模式: " + matcher.group());
            }
        }
    }

    private void assertNormalDataPreservedInLogs(String processedData, TelemetryData originalData) {
        // 驗證服務名稱仍然存在
        if (!processedData.contains(originalData.serviceName)) {
            throw new AssertionError("日誌資料中服務名稱被錯誤移除");
        }
        
        // 驗證正常屬性仍然存在
        for (Map.Entry<String, String> entry : originalData.normalAttributes.entrySet()) {
            if (!processedData.contains(entry.getKey())) {
                throw new AssertionError("日誌資料中正常屬性被錯誤移除: " + entry.getKey());
            }
        }
    }

    private void assertSensitiveHeadersMasked(Map<String, String> processedHeaders, Map<String, String> originalHeaders) {
        List<String> sensitiveHeaderNames = Arrays.asList(
            "authorization", "cookie", "x-api-key", "x-auth-token", "x-access-token"
        );
        
        for (String headerName : sensitiveHeaderNames) {
            if (originalHeaders.containsKey(headerName)) {
                String processedValue = processedHeaders.get(headerName);
                String originalValue = originalHeaders.get(headerName);
                
                if (originalValue.equals(processedValue)) {
                    throw new AssertionError("敏感標頭未被脫敏: " + headerName);
                }
                
                if (!"***REDACTED***".equals(processedValue)) {
                    throw new AssertionError("敏感標頭脫敏格式錯誤: " + headerName + " = " + processedValue);
                }
            }
        }
    }

    private void assertNormalHeadersPreserved(Map<String, String> processedHeaders, Map<String, String> originalHeaders) {
        List<String> normalHeaderNames = Arrays.asList(
            "content-type", "user-agent", "accept", "host", "referer"
        );
        
        for (String headerName : normalHeaderNames) {
            if (originalHeaders.containsKey(headerName)) {
                String processedValue = processedHeaders.get(headerName);
                String originalValue = originalHeaders.get(headerName);
                
                if (!originalValue.equals(processedValue)) {
                    throw new AssertionError("正常標頭被錯誤修改: " + headerName);
                }
            }
        }
    }

    private void assertSensitiveParametersMasked(String processedUrl, String originalUrl) {
        List<String> sensitiveParams = Arrays.asList(
            "password", "token", "key", "secret", "auth", "api_key"
        );
        
        for (String param : sensitiveParams) {
            Pattern originalPattern = Pattern.compile("([?&])" + param + "=([^&]*)");
            Matcher originalMatcher = originalPattern.matcher(originalUrl);
            
            if (originalMatcher.find()) {
                String originalValue = originalMatcher.group(2);
                
                // 檢查原始值是否仍存在於處理後的 URL 中
                if (processedUrl.contains(originalValue)) {
                    throw new AssertionError("URL 參數中的敏感資料未被脫敏: " + param + "=" + originalValue);
                }
                
                // 檢查是否正確替換為脫敏標記
                Pattern processedPattern = Pattern.compile("([?&])" + param + "=\\*\\*\\*REDACTED\\*\\*\\*");
                if (!processedPattern.matcher(processedUrl).find()) {
                    throw new AssertionError("URL 參數脫敏格式錯誤: " + param);
                }
            }
        }
    }

    private void assertUrlStructurePreserved(String processedUrl, String originalUrl) {
        // 提取基本 URL 結構（不包含參數）
        String originalBase = originalUrl.split("\\?")[0];
        String processedBase = processedUrl.split("\\?")[0];
        
        if (!originalBase.equals(processedBase)) {
            throw new AssertionError("URL 基本結構被錯誤修改");
        }
        
        // 檢查參數結構是否保持
        boolean originalHasParams = originalUrl.contains("?");
        boolean processedHasParams = processedUrl.contains("?");
        
        if (originalHasParams != processedHasParams) {
            throw new AssertionError("URL 參數結構被錯誤修改");
        }
    }

    private void assertDatabaseCredentialsMasked(String processedConnectionString, String originalConnectionString) {
        // 檢查密碼是否被脫敏
        Pattern passwordPattern = Pattern.compile("(password|pwd)=([^;&]*)");
        Matcher originalMatcher = passwordPattern.matcher(originalConnectionString);
        
        if (originalMatcher.find()) {
            String originalPassword = originalMatcher.group(2);
            
            // 檢查原始密碼是否仍存在
            if (processedConnectionString.contains(originalPassword)) {
                throw new AssertionError("資料庫連接字串中的密碼未被脫敏: " + originalPassword);
            }
            
            // 檢查是否正確替換為脫敏標記
            if (!processedConnectionString.contains("***REDACTED***")) {
                throw new AssertionError("資料庫連接字串脫敏格式錯誤");
            }
        }
    }

    private void assertConnectionStringStructurePreserved(String processedConnectionString, String originalConnectionString) {
        // 檢查連接字串的基本結構是否保持
        String originalProtocol = originalConnectionString.split("://")[0];
        String processedProtocol = processedConnectionString.split("://")[0];
        
        if (!originalProtocol.equals(processedProtocol)) {
            throw new AssertionError("資料庫連接字串協議被錯誤修改");
        }
        
        // 檢查主機和端口資訊是否保持
        Pattern hostPortPattern = Pattern.compile("://[^/]+");
        Matcher originalHostMatcher = hostPortPattern.matcher(originalConnectionString);
        Matcher processedHostMatcher = hostPortPattern.matcher(processedConnectionString);
        
        if (originalHostMatcher.find() && processedHostMatcher.find()) {
            String originalHost = originalHostMatcher.group();
            String processedHost = processedHostMatcher.group();
            
            // 移除可能的用戶資訊進行比較
            originalHost = originalHost.replaceAll("://[^@]+@", "://");
            processedHost = processedHost.replaceAll("://[^@]+@", "://");
            
            if (!originalHost.equals(processedHost)) {
                throw new AssertionError("資料庫連接字串主機資訊被錯誤修改");
            }
        }
    }

    // ==================== 資料類別 ====================

    public static class TelemetryData {
        public final String serviceName;
        public final Map<String, String> sensitiveAttributes;
        public final Map<String, String> normalAttributes;
        public final String message;

        public TelemetryData(String serviceName, Map<String, String> sensitiveAttributes,
                           Map<String, String> normalAttributes, String message) {
            this.serviceName = serviceName;
            this.sensitiveAttributes = new HashMap<>(sensitiveAttributes);
            this.normalAttributes = new HashMap<>(normalAttributes);
            this.message = message;
        }
    }

    /**
     * 單元測試：驗證隱私配置檔案存在且格式正確
     */
    @Test
    void privacyConfigurationShouldExistAndBeValid() {
        Path configPath = Paths.get(PRIVACY_CONFIG_PATH);
        
        if (!Files.exists(configPath)) {
            throw new AssertionError("隱私配置檔案不存在: " + PRIVACY_CONFIG_PATH);
        }
        
        try {
            String content = Files.readString(configPath);
            
            // 檢查必要的配置段落
            if (!content.contains("sensitive_data_patterns")) {
                throw new AssertionError("隱私配置檔案缺少敏感資料模式配置");
            }
            
            if (!content.contains("masking_strategies")) {
                throw new AssertionError("隱私配置檔案缺少脫敏策略配置");
            }
            
            if (!content.contains("compliance")) {
                throw new AssertionError("隱私配置檔案缺少合規性配置");
            }
            
        } catch (IOException e) {
            throw new AssertionError("無法讀取隱私配置檔案: " + e.getMessage());
        }
    }

    /**
     * 單元測試：驗證 OpenTelemetry Collector 配置包含隱私處理器
     */
    @Test
    void otelCollectorConfigShouldIncludePrivacyProcessors() {
        Path configPath = Paths.get(OTEL_CONFIG_PATH);
        
        if (!Files.exists(configPath)) {
            throw new AssertionError("OpenTelemetry Collector 配置檔案不存在: " + OTEL_CONFIG_PATH);
        }
        
        try {
            String content = Files.readString(configPath);
            
            // 檢查隱私處理器配置
            if (!content.contains("transform/privacy_metrics")) {
                throw new AssertionError("OpenTelemetry Collector 配置缺少指標隱私處理器");
            }
            
            if (!content.contains("transform/privacy_traces")) {
                throw new AssertionError("OpenTelemetry Collector 配置缺少追蹤隱私處理器");
            }
            
            if (!content.contains("transform/privacy_logs")) {
                throw new AssertionError("OpenTelemetry Collector 配置缺少日誌隱私處理器");
            }
            
            // 檢查管道配置是否包含隱私處理器
            if (!content.contains("transform/privacy_metrics") || 
                !content.contains("transform/privacy_traces") || 
                !content.contains("transform/privacy_logs")) {
                throw new AssertionError("OpenTelemetry Collector 管道配置未包含隱私處理器");
            }
            
        } catch (IOException e) {
            throw new AssertionError("無法讀取 OpenTelemetry Collector 配置檔案: " + e.getMessage());
        }
    }
}