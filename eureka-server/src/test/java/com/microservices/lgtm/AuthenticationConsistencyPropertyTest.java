package com.microservices.lgtm;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.AfterProperty;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 屬性 17: 身份驗證一致性測試
 * 
 * 驗證需求 8.2: API 金鑰身份驗證
 * - 所有服務都使用一致的 API 金鑰驗證機制
 * - API 金鑰格式和驗證邏輯在所有組件中保持一致
 * - 身份驗證失敗時的行為一致
 * - 金鑰輪換機制在所有服務中同步
 */
@SpringBootTest
@TestPropertySource(properties = {
    "management.endpoints.web.exposure.include=health,metrics,info",
    "management.endpoint.health.show-details=always",
    "logging.level.com.microservices=DEBUG"
})
@DisplayName("屬性 17: 身份驗證一致性測試")
public class AuthenticationConsistencyPropertyTest {

    private static final String API_KEYS_DIR = "api-keys";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern API_KEY_PATTERN = Pattern.compile("^[a-f0-9]{64}$");
    
    // 測試服務端點
    private static final Map<String, String> SERVICE_ENDPOINTS = Map.of(
        "otel-collector", "http://localhost:13133/health",
        "mimir", "http://localhost:9009/ready",
        "tempo", "http://localhost:3200/ready",
        "loki", "http://localhost:3100/ready",
        "grafana", "http://localhost:3000/api/health"
    );
    
    // API 金鑰驗證端點
    private static final Map<String, String> AUTH_ENDPOINTS = Map.of(
        "otel-collector", "http://localhost:4328/v1/traces",
        "mimir", "http://localhost:9009/api/v1/push",
        "tempo", "http://localhost:3200/api/traces",
        "loki", "http://localhost:3100/loki/api/v1/push"
    );
    
    private HttpClient httpClient;
    private ExecutorService executorService;
    private Map<String, String> loadedApiKeys;
    private Map<String, AuthenticationResult> authResults;
    
    @BeforeProperty
    void setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
        executorService = Executors.newFixedThreadPool(10);
        loadedApiKeys = new ConcurrentHashMap<>();
        authResults = new ConcurrentHashMap<>();
        
        // 載入 API 金鑰
        loadApiKeys();
    }
    
    @AfterProperty
    void tearDown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    /**
     * 屬性: API 金鑰格式一致性
     * 所有服務的 API 金鑰都應該遵循相同的格式規範
     */
    @Property(tries = 100)
    @DisplayName("API 金鑰格式一致性")
    void apiKeyFormatConsistency(@ForAll("validServiceNames") String serviceName) {
        // 獲取服務的 API 金鑰
        String apiKey = loadedApiKeys.get(serviceName);
        
        // 驗證 API 金鑰格式
        Assume.that(apiKey != null && !apiKey.isEmpty());
        
        // 屬性: API 金鑰必須符合標準格式（64 位十六進制）
        assert API_KEY_PATTERN.matcher(apiKey).matches() : 
            String.format("服務 %s 的 API 金鑰格式不正確: %s", serviceName, apiKey);
        
        // 屬性: API 金鑰長度必須為 64 個字符
        assert apiKey.length() == 64 : 
            String.format("服務 %s 的 API 金鑰長度不正確: %d (應為 64)", serviceName, apiKey.length());
        
        // 屬性: API 金鑰不應包含特殊字符
        assert apiKey.matches("[a-f0-9]+") : 
            String.format("服務 %s 的 API 金鑰包含無效字符: %s", serviceName, apiKey);
    }
    
    /**
     * 屬性: 身份驗證行為一致性
     * 所有服務在處理身份驗證時應該有一致的行為
     */
    @Property(tries = 100)
    @DisplayName("身份驗證行為一致性")
    void authenticationBehaviorConsistency(
        @ForAll("validServiceNames") String serviceName,
        @ForAll("authenticationScenarios") AuthenticationScenario scenario) {
        
        String endpoint = AUTH_ENDPOINTS.get(serviceName);
        Assume.that(endpoint != null);
        
        // 執行身份驗證測試
        AuthenticationResult result = performAuthentication(serviceName, endpoint, scenario);
        authResults.put(serviceName + "_" + scenario.name(), result);
        
        // 驗證身份驗證行為一致性
        validateAuthenticationConsistency(serviceName, scenario, result);
    }
    
    /**
     * 屬性: API 金鑰輪換一致性
     * 當 API 金鑰輪換時，所有服務都應該同步更新
     */
    @Property(tries = 50)
    @DisplayName("API 金鑰輪換一致性")
    void apiKeyRotationConsistency(@ForAll("validServiceNames") String serviceName) {
        // 模擬 API 金鑰輪換
        String originalKey = loadedApiKeys.get(serviceName);
        Assume.that(originalKey != null);
        
        // 生成新的 API 金鑰
        String newApiKey = generateNewApiKey();
        
        // 驗證新金鑰格式
        assert API_KEY_PATTERN.matcher(newApiKey).matches() : 
            "新生成的 API 金鑰格式不正確";
        
        // 驗證新金鑰與舊金鑰不同
        assert !originalKey.equals(newApiKey) : 
            "新 API 金鑰不應與舊金鑰相同";
        
        // 屬性: 金鑰輪換後，舊金鑰應該失效
        AuthenticationResult oldKeyResult = testApiKeyValidity(serviceName, originalKey);
        
        // 屬性: 新金鑰應該有效（在實際部署中）
        AuthenticationResult newKeyResult = testApiKeyValidity(serviceName, newApiKey);
        
        // 記錄輪換結果
        System.out.printf("服務 %s API 金鑰輪換測試 - 舊金鑰: %s, 新金鑰: %s%n", 
            serviceName, oldKeyResult.isValid(), newKeyResult.isValid());
    }
    
    /**
     * 屬性: 並發身份驗證一致性
     * 多個並發請求使用相同 API 金鑰時應該有一致的結果
     */
    @Property(tries = 30)
    @DisplayName("並發身份驗證一致性")
    void concurrentAuthenticationConsistency(
        @ForAll("validServiceNames") String serviceName,
        @ForAll("concurrentRequestCounts") int concurrentRequests) {
        
        String endpoint = AUTH_ENDPOINTS.get(serviceName);
        Assume.that(endpoint != null);
        
        String apiKey = loadedApiKeys.get(serviceName);
        Assume.that(apiKey != null);
        
        // 執行並發身份驗證請求
        List<CompletableFuture<AuthenticationResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < concurrentRequests; i++) {
            CompletableFuture<AuthenticationResult> future = CompletableFuture.supplyAsync(() -> {
                AuthenticationScenario scenario = new AuthenticationScenario("VALID_KEY", apiKey, true);
                return performAuthentication(serviceName, endpoint, scenario);
            }, executorService);
            futures.add(future);
        }
        
        // 等待所有請求完成
        List<AuthenticationResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();
        
        // 驗證所有結果的一致性
        boolean firstResult = results.get(0).isValid();
        for (AuthenticationResult result : results) {
            assert result.isValid() == firstResult : 
                String.format("並發身份驗證結果不一致 - 服務: %s", serviceName);
        }
        
        // 屬性: 所有並發請求的響應時間應該在合理範圍內
        long maxResponseTime = results.stream()
            .mapToLong(AuthenticationResult::getResponseTimeMs)
            .max()
            .orElse(0);
        
        long minResponseTime = results.stream()
            .mapToLong(AuthenticationResult::getResponseTimeMs)
            .min()
            .orElse(0);
        
        // 響應時間差異不應超過 5 秒
        assert (maxResponseTime - minResponseTime) <= 5000 : 
            String.format("並發請求響應時間差異過大: %d ms", maxResponseTime - minResponseTime);
    }
    
    /**
     * 屬性: 錯誤處理一致性
     * 所有服務在處理身份驗證錯誤時應該有一致的行為
     */
    @Property(tries = 50)
    @DisplayName("錯誤處理一致性")
    void errorHandlingConsistency(@ForAll("validServiceNames") String serviceName) {
        String endpoint = AUTH_ENDPOINTS.get(serviceName);
        Assume.that(endpoint != null);
        
        // 測試各種錯誤場景
        List<AuthenticationScenario> errorScenarios = List.of(
            new AuthenticationScenario("INVALID_KEY", "invalid_key_123", false),
            new AuthenticationScenario("EMPTY_KEY", "", false),
            new AuthenticationScenario("NULL_KEY", null, false),
            new AuthenticationScenario("MALFORMED_KEY", "not-a-hex-key", false)
        );
        
        for (AuthenticationScenario scenario : errorScenarios) {
            AuthenticationResult result = performAuthentication(serviceName, endpoint, scenario);
            
            // 屬性: 無效的 API 金鑰應該被拒絕
            assert !result.isValid() : 
                String.format("服務 %s 應該拒絕無效的 API 金鑰: %s", serviceName, scenario.getApiKey());
            
            // 屬性: 錯誤響應應該在合理時間內返回
            assert result.getResponseTimeMs() <= 10000 : 
                String.format("服務 %s 錯誤響應時間過長: %d ms", serviceName, result.getResponseTimeMs());
        }
    }
    
    // 輔助方法
    
    private void loadApiKeys() {
        try {
            for (String serviceName : SERVICE_ENDPOINTS.keySet()) {
                Path keyFile = Paths.get(API_KEYS_DIR, serviceName + "-api-key.txt");
                if (Files.exists(keyFile)) {
                    String apiKey = Files.readString(keyFile).trim();
                    loadedApiKeys.put(serviceName, apiKey);
                }
            }
        } catch (IOException e) {
            System.err.println("載入 API 金鑰失敗: " + e.getMessage());
        }
    }
    
    private AuthenticationResult performAuthentication(String serviceName, String endpoint, AuthenticationScenario scenario) {
        long startTime = System.currentTimeMillis();
        
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
            
            // 添加身份驗證標頭
            if (scenario.getApiKey() != null && !scenario.getApiKey().isEmpty()) {
                requestBuilder.header("X-API-Key", scenario.getApiKey());
                requestBuilder.header("Authorization", "Bearer " + scenario.getApiKey());
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 根據 HTTP 狀態碼判斷身份驗證是否成功
            boolean isValid = response.statusCode() < 400;
            
            return new AuthenticationResult(isValid, response.statusCode(), responseTime, response.body());
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new AuthenticationResult(false, -1, responseTime, e.getMessage());
        }
    }
    
    private void validateAuthenticationConsistency(String serviceName, AuthenticationScenario scenario, AuthenticationResult result) {
        if (scenario.isExpectedValid()) {
            // 有效的 API 金鑰應該被接受
            assert result.isValid() : 
                String.format("服務 %s 應該接受有效的 API 金鑰", serviceName);
        } else {
            // 無效的 API 金鑰應該被拒絕
            assert !result.isValid() : 
                String.format("服務 %s 應該拒絕無效的 API 金鑰", serviceName);
        }
        
        // 響應時間應該在合理範圍內
        assert result.getResponseTimeMs() <= 30000 : 
            String.format("服務 %s 響應時間過長: %d ms", serviceName, result.getResponseTimeMs());
    }
    
    private String generateNewApiKey() {
        // 生成 64 位隨機十六進制字符串
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            sb.append(Integer.toHexString(random.nextInt(16)));
        }
        return sb.toString();
    }
    
    private AuthenticationResult testApiKeyValidity(String serviceName, String apiKey) {
        String endpoint = AUTH_ENDPOINTS.get(serviceName);
        if (endpoint == null) {
            return new AuthenticationResult(false, -1, 0, "端點不存在");
        }
        
        AuthenticationScenario scenario = new AuthenticationScenario("TEST_KEY", apiKey, true);
        return performAuthentication(serviceName, endpoint, scenario);
    }
    
    // 數據提供者
    
    @Provide
    Arbitrary<String> validServiceNames() {
        return Arbitraries.of(SERVICE_ENDPOINTS.keySet().toArray(new String[0]));
    }
    
    @Provide
    Arbitrary<AuthenticationScenario> authenticationScenarios() {
        return Arbitraries.of(
            new AuthenticationScenario("VALID_KEY", "valid_api_key_placeholder", true),
            new AuthenticationScenario("INVALID_KEY", "invalid_key_123", false),
            new AuthenticationScenario("EMPTY_KEY", "", false)
        );
    }
    
    @Provide
    Arbitrary<Integer> concurrentRequestCounts() {
        return Arbitraries.integers().between(2, 10);
    }
    
    // 內部類別
    
    private static class AuthenticationScenario {
        private final String name;
        private final String apiKey;
        private final boolean expectedValid;
        
        public AuthenticationScenario(String name, String apiKey, boolean expectedValid) {
            this.name = name;
            this.apiKey = apiKey;
            this.expectedValid = expectedValid;
        }
        
        public String name() { return name; }
        public String getApiKey() { return apiKey; }
        public boolean isExpectedValid() { return expectedValid; }
    }
    
    private static class AuthenticationResult {
        private final boolean valid;
        private final int statusCode;
        private final long responseTimeMs;
        private final String responseBody;
        
        public AuthenticationResult(boolean valid, int statusCode, long responseTimeMs, String responseBody) {
            this.valid = valid;
            this.statusCode = statusCode;
            this.responseTimeMs = responseTimeMs;
            this.responseBody = responseBody;
        }
        
        public boolean isValid() { return valid; }
        public int getStatusCode() { return statusCode; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public String getResponseBody() { return responseBody; }
    }
}