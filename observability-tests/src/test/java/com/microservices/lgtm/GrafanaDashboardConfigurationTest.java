package com.microservices.lgtm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 驗證 Grafana 儀表板配置的正確性
 * 
 * 功能: opentelemetry-lgtm-integration
 * 驗證需求: 5.1, 5.2, 5.3, 5.4
 */
@DisplayName("Grafana 儀表板配置驗證測試")
public class GrafanaDashboardConfigurationTest {

    private static final String GRAFANA_DASHBOARDS_PATH = "../grafana/dashboards";
    private static final String GRAFANA_PROVISIONING_PATH = "../grafana/provisioning/dashboards";
    
    private ObjectMapper objectMapper;
    private Path projectRoot;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 從測試目錄向上找到專案根目錄
        projectRoot = Paths.get("").toAbsolutePath();
        while (!Files.exists(projectRoot.resolve("../docker-compose.yml"))) {
            projectRoot = projectRoot.getParent();
            if (projectRoot == null) {
                fail("無法找到專案根目錄");
            }
        }
    }

    @Test
    @DisplayName("驗證儀表板配置檔案存在")
    void testDashboardProvisioningConfigExists() {
        Path provisioningConfig = projectRoot.resolve(GRAFANA_PROVISIONING_PATH).resolve("dashboards.yaml");
        assertTrue(Files.exists(provisioningConfig), 
            "儀表板配置檔案應該存在: " + provisioningConfig);
    }

    @Test
    @DisplayName("驗證微服務概覽儀表板存在且配置正確")
    void testMicroservicesOverviewDashboardExists() throws IOException {
        // 需求 5.1: 微服務概覽儀表板
        Path dashboardPath = projectRoot.resolve(GRAFANA_DASHBOARDS_PATH).resolve("microservices-overview.json");
        assertTrue(Files.exists(dashboardPath), 
            "微服務概覽儀表板應該存在: " + dashboardPath);

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        
        // 驗證儀表板基本屬性
        assertEquals("微服務概覽儀表板", dashboard.get("title").asText());
        assertEquals("microservices-overview", dashboard.get("uid").asText());
        
        // 驗證包含必要的標籤
        JsonNode tags = dashboard.get("tags");
        assertNotNull(tags);
        assertTrue(containsTag(tags, "microservices"));
        assertTrue(containsTag(tags, "overview"));
        
        // 驗證包含必要的面板
        JsonNode panels = dashboard.get("panels");
        assertNotNull(panels);
        assertTrue(panels.size() > 0, "儀表板應該包含面板");
        
        // 驗證包含服務健康狀況面板
        boolean hasHealthPanel = false;
        for (JsonNode panel : panels) {
            String title = panel.get("title").asText();
            if (title.contains("健康狀況") || title.contains("服務狀態")) {
                hasHealthPanel = true;
                break;
            }
        }
        assertTrue(hasHealthPanel, "應該包含服務健康狀況面板");
    }

    @Test
    @DisplayName("驗證服務詳細儀表板存在且配置正確")
    void testServiceDetailsDashboardExists() throws IOException {
        // 需求 5.2: 服務詳細儀表板
        Path dashboardPath = projectRoot.resolve(GRAFANA_DASHBOARDS_PATH).resolve("service-details.json");
        assertTrue(Files.exists(dashboardPath), 
            "服務詳細儀表板應該存在: " + dashboardPath);

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        
        // 驗證儀表板基本屬性
        assertEquals("服務詳細監控儀表板", dashboard.get("title").asText());
        assertEquals("service-details", dashboard.get("uid").asText());
        
        // 驗證包含服務選擇變數
        JsonNode templating = dashboard.get("templating");
        assertNotNull(templating);
        JsonNode variables = templating.get("list");
        assertNotNull(variables);
        
        boolean hasServiceVariable = false;
        for (JsonNode variable : variables) {
            if ("service".equals(variable.get("name").asText())) {
                hasServiceVariable = true;
                break;
            }
        }
        assertTrue(hasServiceVariable, "應該包含服務選擇變數");
        
        // 驗證包含 JVM、HTTP 相關面板
        JsonNode panels = dashboard.get("panels");
        assertNotNull(panels);
        
        boolean hasJvmPanel = false;
        boolean hasHttpPanel = false;
        
        for (JsonNode panel : panels) {
            String title = panel.get("title").asText().toLowerCase();
            if (title.contains("jvm") || title.contains("記憶體") || title.contains("cpu")) {
                hasJvmPanel = true;
            }
            if (title.contains("http") || title.contains("請求")) {
                hasHttpPanel = true;
            }
        }
        
        assertTrue(hasJvmPanel, "應該包含 JVM 相關面板");
        assertTrue(hasHttpPanel, "應該包含 HTTP 相關面板");
    }

    @Test
    @DisplayName("驗證分散式追蹤儀表板存在且配置正確")
    void testDistributedTracingDashboardExists() throws IOException {
        // 需求 5.3: 分散式追蹤儀表板
        Path dashboardPath = projectRoot.resolve(GRAFANA_DASHBOARDS_PATH).resolve("distributed-tracing.json");
        assertTrue(Files.exists(dashboardPath), 
            "分散式追蹤儀表板應該存在: " + dashboardPath);

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        
        // 驗證儀表板基本屬性
        assertEquals("分散式追蹤監控儀表板", dashboard.get("title").asText());
        assertEquals("distributed-tracing", dashboard.get("uid").asText());
        
        // 驗證包含追蹤相關標籤
        JsonNode tags = dashboard.get("tags");
        assertNotNull(tags);
        assertTrue(containsTag(tags, "tracing"));
        assertTrue(containsTag(tags, "tempo"));
        
        // 驗證包含 Tempo 資料源的面板
        JsonNode panels = dashboard.get("panels");
        assertNotNull(panels);
        
        boolean hasTempoPanel = false;
        for (JsonNode panel : panels) {
            JsonNode datasource = panel.get("datasource");
            if (datasource != null && "tempo".equals(datasource.get("type").asText())) {
                hasTempoPanel = true;
                break;
            }
        }
        assertTrue(hasTempoPanel, "應該包含使用 Tempo 資料源的面板");
    }

    @Test
    @DisplayName("驗證錯誤監控儀表板存在且配置正確")
    void testErrorMonitoringDashboardExists() throws IOException {
        // 需求 5.4: 錯誤監控儀表板
        Path dashboardPath = projectRoot.resolve(GRAFANA_DASHBOARDS_PATH).resolve("error-monitoring.json");
        assertTrue(Files.exists(dashboardPath), 
            "錯誤監控儀表板應該存在: " + dashboardPath);

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        
        // 驗證儀表板基本屬性
        assertEquals("錯誤監控與告警儀表板", dashboard.get("title").asText());
        assertEquals("error-monitoring", dashboard.get("uid").asText());
        
        // 驗證包含錯誤相關標籤
        JsonNode tags = dashboard.get("tags");
        assertNotNull(tags);
        assertTrue(containsTag(tags, "errors"));
        assertTrue(containsTag(tags, "alerts"));
        
        // 驗證包含錯誤監控相關面板
        JsonNode panels = dashboard.get("panels");
        assertNotNull(panels);
        
        boolean hasErrorRatePanel = false;
        boolean hasLogPanel = false;
        
        for (JsonNode panel : panels) {
            String title = panel.get("title").asText().toLowerCase();
            if (title.contains("錯誤") && title.contains("率")) {
                hasErrorRatePanel = true;
            }
            
            JsonNode datasource = panel.get("datasource");
            if (datasource != null && "loki".equals(datasource.get("type").asText())) {
                hasLogPanel = true;
            }
        }
        
        assertTrue(hasErrorRatePanel, "應該包含錯誤率面板");
        assertTrue(hasLogPanel, "應該包含使用 Loki 資料源的日誌面板");
    }

    @Test
    @DisplayName("驗證所有儀表板都有正確的資料源配置")
    void testAllDashboardsHaveCorrectDataSources() throws IOException {
        List<String> dashboardFiles = Arrays.asList(
            "microservices-overview.json",
            "service-details.json", 
            "distributed-tracing.json",
            "error-monitoring.json"
        );
        
        for (String filename : dashboardFiles) {
            Path dashboardPath = projectRoot.resolve(GRAFANA_DASHBOARDS_PATH).resolve(filename);
            JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
            
            JsonNode panels = dashboard.get("panels");
            assertNotNull(panels, filename + " 應該包含面板");
            
            // 驗證每個面板都有正確的資料源配置
            for (JsonNode panel : panels) {
                JsonNode datasource = panel.get("datasource");
                if (datasource != null) {
                    String type = datasource.get("type").asText();
                    String uid = datasource.get("uid").asText();
                    
                    // 驗證資料源類型和 UID 的對應關係
                    switch (type) {
                        case "prometheus":
                            assertEquals("mimir", uid, "Prometheus 類型應該使用 mimir UID");
                            break;
                        case "tempo":
                            assertEquals("tempo", uid, "Tempo 類型應該使用 tempo UID");
                            break;
                        case "loki":
                            assertEquals("loki", uid, "Loki 類型應該使用 loki UID");
                            break;
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("驗證儀表板 JSON 格式正確性")
    void testDashboardJsonValidity() throws IOException {
        List<String> dashboardFiles = Arrays.asList(
            "microservices-overview.json",
            "service-details.json", 
            "distributed-tracing.json",
            "error-monitoring.json"
        );
        
        for (String filename : dashboardFiles) {
            Path dashboardPath = projectRoot.resolve(GRAFANA_DASHBOARDS_PATH).resolve(filename);
            
            // 嘗試解析 JSON，如果格式錯誤會拋出異常
            assertDoesNotThrow(() -> {
                objectMapper.readTree(dashboardPath.toFile());
            }, filename + " 應該是有效的 JSON 格式");
        }
    }

    @Test
    @DisplayName("驗證儀表板配置檔案 YAML 格式正確性")
    void testProvisioningYamlValidity() {
        Path provisioningConfig = projectRoot.resolve(GRAFANA_PROVISIONING_PATH).resolve("dashboards.yaml");
        
        // 驗證檔案存在且可讀取
        assertTrue(Files.exists(provisioningConfig));
        assertTrue(Files.isReadable(provisioningConfig));
        
        // 驗證檔案不為空
        assertDoesNotThrow(() -> {
            String content = Files.readString(provisioningConfig);
            assertFalse(content.trim().isEmpty(), "配置檔案不應該為空");
            assertTrue(content.contains("providers"), "配置檔案應該包含 providers 配置");
            assertTrue(content.contains("microservices-dashboards"), "配置檔案應該包含儀表板提供者配置");
        });
    }

    /**
     * 檢查標籤陣列是否包含指定的標籤
     */
    private boolean containsTag(JsonNode tags, String tag) {
        for (JsonNode tagNode : tags) {
            if (tag.equals(tagNode.asText())) {
                return true;
            }
        }
        return false;
    }
}