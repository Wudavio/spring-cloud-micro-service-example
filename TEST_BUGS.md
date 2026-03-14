# 測試執行 Bug 記錄

> 記錄日期：2026-03-15
> 測試環境：本機開發環境（無 Docker，純 Maven unit tests）
> 測試範圍：eureka-server、config-server、api-gateway、product-service、inventory-service、order-service、observability-tests

---

## 各服務測試結果總覽

| 服務 | 測試數 | 失敗 | 錯誤 | 結果 |
|------|--------|------|------|------|
| eureka-server | 20 | 0 | 0 | ✅ PASS |
| config-server | 6 | 0 | 0 | ✅ PASS |
| api-gateway | 4 | 0 | 0 | ✅ PASS |
| product-service | 24 | 0 | 0 | ✅ PASS |
| order-service | 8 | 0 | 0 | ✅ PASS |
| auth-service | 0 | 0 | 0 | ⚪ 無測試 |
| inventory-service | 54 | 5 | 0 | ❌ FAIL |
| observability-tests | 55 | 9 | 13 | ❌ FAIL |

---

## TEST-BUG-001：inventory-service `RetryMechanismPropertyTest` 5 項測試失敗

**嚴重程度：** 🔴 高（反映真實實作邏輯缺陷）

**狀態：** 開啟

**症狀：**
```
Tests run: 6, Failures: 5, Errors: 0 -- in RetryMechanismPropertyTest
testRetryMechanismExhaustion                <<< FAILURE
testAdjustTemporaryReservationRetry         <<< FAILURE
testRetryMechanismWithSystemBusy            <<< FAILURE
testRetryMechanismWithOptimisticLockingFailure <<< FAILURE
testConfirmReservationRetry                 <<< FAILURE
```

**Root Cause：**

| 子項 | 測試期望 | 實際行為 | 原因 |
|------|---------|---------|------|
| `testRetryMechanismExhaustion` | 重試耗盡後拋 `InsufficientStockException` | 拋 `SystemBusyException` | `RetryableInventoryService` 將鎖取得失敗（`LockAcquisitionException`）包裝成 `SystemBusyException`，優先於業務例外拋出 |
| `testAdjustTemporaryReservationRetry` | 3 次重試後正常完成 | 拋 `RetryExhaustedException` | 測試 Mock 沒有阻止 `DistributedLockService` 被實際呼叫，鎖取得 3 次皆失敗 |
| `testRetryMechanismWithSystemBusy` | 重試後成功 | 仍拋 `SystemBusyException` | 同上，鎖機制在 mock 環境未被正確隔離 |
| `testRetryMechanismWithOptimisticLockingFailure` | 重試次數 > 0 | 重試次數 = 0 | 樂觀鎖失敗未被計入 Spring Retry 的重試次數統計 |
| `testConfirmReservationRetry` | 重試後成功 | 拋 `RetryExhaustedException` | 同鎖機制問題 |

**相關程式碼：**
```
inventory-service/src/main/java/com/microservices/inventory/service/RetryableInventoryService.java:58
inventory-service/src/test/java/com/microservices/inventory/service/RetryMechanismPropertyTest.java
```

**修復建議：**
1. 測試中加入 `@MockBean DistributedLockService`，讓 mock 直接執行 callback 而不嘗試取得真實鎖
2. 明確指定 `RetryableInventoryService` 的例外優先順序：業務例外（`InsufficientStockException`）應覆蓋系統例外（`SystemBusyException`）

---

## TEST-BUG-002：auth-service 完全沒有測試

**嚴重程度：** 🔴 高（系統安全核心無任何測試保護）

**狀態：** 開啟

**症狀：**
```
auth-service/src/test/ 目錄不存在或為空
mvn test -pl auth-service → BUILD SUCCESS（0 tests run）
```

**Root Cause：**
auth-service 的核心安全邏輯（JWT 簽發、驗證、過濾器）完全沒有測試。這與 SERVICE_ISSUES.md T-1 呼應，但問題比 T-1 描述的更嚴重——不只是缺少 JWT 測試，而是整個服務完全沒有任何測試。

**修復建議：**
補充以下最低限度測試：
- `JwtUtilTest`：過期 Token 拒絕、篡改簽名拒絕、修改 Payload 拒絕
- `AuthControllerTest`：register / login 成功/失敗流程
- `JwtAuthenticationFilterTest`：有效 Token 放行、無效 Token 攔截

---

## TEST-BUG-003：observability-tests `@TempDir` 在 jqwik `@Property` 方法中無法注入（NPE）

**嚴重程度：** 🟡 中（測試框架使用方式錯誤）

**狀態：** 開啟

**影響測試：** `ConfigurationFaultTolerancePropertyTest`（4 errors）、`DynamicConfigurationResponsivenessPropertyTest`（5 errors）

**症狀：**
```
java.lang.NullPointerException:
  Cannot invoke "java.nio.file.Path.resolve(String)"
  because "this.tempDir" is null
  at ConfigurationFaultTolerancePropertyTest.createConfigFile(line:255)

java.lang.NullPointerException:
  Cannot invoke "java.nio.file.Path.resolve(String)"
  because "this.tempConfigDir" is null
  at DynamicConfigurationResponsivenessPropertyTest.createInitialConfigFile(line:283)
```

**Root Cause：**
兩個測試類別都以 JUnit 5 `@TempDir` 注入 `Path` 欄位，但方法是 jqwik `@Property`。jqwik 的 lifecycle 與 JUnit 5 不完全兼容，`@TempDir` 欄位在 `@Property` 方法執行前**不會被 JUnit 的 extension 注入**，導致欄位永遠是 `null`。

```java
// 問題寫法（jqwik @Property 中使用 JUnit @TempDir）
@TempDir
Path tempDir;  // 永遠是 null

@Property
void someTest() {
    tempDir.resolve("config.yaml");  // NPE
}
```

**修復建議：**
在 `@Property` 方法內手動建立臨時目錄，或改用 `@BeforeEach` 搭配 `@TempDir`（JUnit 方法層級注入）：
```java
@Property
void someTest() throws IOException {
    Path tempDir = Files.createTempDirectory("test-");
    try {
        // test logic
    } finally {
        // cleanup
    }
}
```

---

## TEST-BUG-004：observability-tests `ConfigurationEnvironmentAdaptabilityPropertyTest` 缺少 config-management 子目錄結構

**嚴重程度：** 🟡 中（測試所依賴的基礎設施不完整）

**狀態：** 開啟

**影響測試：** `ConfigurationEnvironmentAdaptabilityPropertyTest`（3 errors）

**症狀：**
```
java.lang.RuntimeException: 配置管理目錄不存在:
  .../otel-agent/config-management/environments/
  .../otel-agent/config-management/services/
  .../otel-agent/config-management/generate-configs.sh
```

**Root Cause：**
`otel-agent/` 目錄下有 `config-management/` 目錄，但缺少測試所期望的子結構：
- `otel-agent/config-management/environments/{development,testing,staging,production}.env`
- `otel-agent/config-management/services/{api-gateway,auth-service,...}.template`
- `otel-agent/config-management/generate-configs.sh`

**修復建議：**
建立對應的目錄結構與檔案，或修改測試使其適應現有的 `otel-agent/config-management/` 結構（目前只有 `generated-configs/` 子目錄）。

---

## TEST-BUG-005：observability-tests `DataPrivacyProtectionPropertyTest` shell 腳本執行失敗

**嚴重程度：** 🟡 中（shell 相容性問題）

**狀態：** 開啟

**影響測試：** `DataPrivacyProtectionPropertyTest`（4 failures）

**症狀：**
```
(eval):[:7: integer expression expected: 0\n0
```

**Root Cause：**
`otel-agent/data-privacy-filter.sh` 腳本在 macOS（zsh/bash）執行時出現 `integer expression expected` 錯誤。腳本可能使用了 Linux-only 的 bash 語法（如 `[[ ]]` 搭配 `=~` 正則，或某些整數比較語法），或是腳本本身的換行符為 `\r\n`（Windows format）導致語法解析失敗。

**修復建議：**
1. 確認腳本換行符為 Unix LF：`file data-privacy-filter.sh`
2. 若為 CRLF，轉換：`sed -i 's/\r//' data-privacy-filter.sh`
3. 添加 `#!/bin/bash` shebang 並測試 macOS 兼容性

---

## TEST-BUG-006：observability-tests OTel Collector 配置缺少多項進階功能

**嚴重程度：** 🟠 低（測試驗證的功能尚未實作）

**狀態：** 開啟

**影響測試：**
- `LogLevelFilteringPropertyTest.otelCollectorShouldHaveLogFilterConfiguration` (1 failure)
- `NonBlockingExportPropertyTest.backupMechanismShouldBeConfigured` (1 failure)
- `NonBlockingExportPropertyTest.resourceLimitsShouldPreventBlocking` (1 failure)
- `StructuredLogIntegrityPropertyTest.otelCollectorShouldHaveLogExportConfiguration` (2 failures)

**症狀：**
```
AssertionError: [Collector 配置應該包含日誌過濾處理器]
  Expecting actual: "# OpenTelemetry Collector 簡化配置檔案..."
  to contain: "filter/log_level_filter"

AssertionError: [配置應該包含檔案備份導出器]
  to contain: "file"
```

**Root Cause：**
`otel-collector-config.yaml` 是簡化版配置，未包含：
- Log 等級過濾 processor（`filter/log_level_filter`）
- 檔案備份 exporter（`file` exporter）
- 資源使用限制配置（memory_limiter 等）
- 完整的 log 導出 pipeline 設定（test 期望特定的 exporter 名稱）

**修復建議：**
依實際需求在 `otel-collector-config.yaml` 補充所缺功能，或修改測試使其符合現有配置的實際結構。

---

## TEST-BUG-007：observability-tests `LogTraceCorrelationPropertyTest` 單元測試中無 OTel trace context

**嚴重程度：** 🟠 低（單元測試環境限制，非 production 問題）

**狀態：** 開啟

**影響測試：** `LogTraceCorrelationPropertyTest`（2 failures）

**症狀：**
```
AssertionError: [日誌應該包含正確格式的 trace 上下文]
Expecting actual:
  "00:23:07.468 [main] INFO ... -- ????"
to contain:
  "[1234567890abcdef,fedcba0987654321]"
```

**Root Cause：**
測試期望 log 輸出中含有 `[traceId,spanId]` 格式的 trace context，但：
1. 單元測試環境沒有掛載 OTel Java Agent，不會自動注入 trace context 至 MDC
2. 日誌中的中文顯示為 `????`，代表控制台 encoding 不是 UTF-8

**修復建議：**
1. 在測試中使用 OpenTelemetry SDK 手動建立 span，並設定 MDC：
```java
Span span = tracer.spanBuilder("test").startSpan();
try (Scope scope = span.makeCurrent()) {
    MDC.put("traceId", span.getSpanContext().getTraceId());
    // log here
}
```
2. 或修改測試只驗證 log 格式（pattern 包含 traceId 欄位），不驗證特定值

---

## 測試執行驗證結果

| 服務 | 測試類別 | 結果 |
|------|---------|------|
| eureka-server | `EurekaServerIntegrationTest` | ✅ 通過 |
| eureka-server | `EndToEndTelemetryIntegrationTest` | ✅ 通過 |
| eureka-server | `PerformanceBenchmarkTest` | ✅ 通過 |
| config-server | `ConfigServerIntegrationTest` | ✅ 通過 |
| api-gateway | `ApiGatewayIntegrationTest` | ✅ 通過 |
| product-service | `ProductServiceIntegrationTest` | ✅ 通過 |
| product-service | `ProductCrudPropertyTest` | ✅ 通過 |
| product-service | `ProductDeletionConstraintPropertyTest` | ✅ 通過 |
| product-service | `ProductQueryIntegrityPropertyTest` | ✅ 通過 |
| product-service | `ProductStatusChangeNotificationPropertyTest` | ✅ 通過 |
| order-service | `CartInventorySyncPropertyTest` | ✅ 通過 |
| order-service | `RetryMechanismPropertyTest` | ✅ 通過 |
| order-service | `SimpleCartServiceTest` | ✅ 通過 |
| inventory-service | `InventoryReservationAtomicityPropertyTest` | ✅ 通過 |
| inventory-service | `LowStockAlertPropertyTest` | ✅ 通過 |
| inventory-service | `DistributedLockMutualExclusionPropertyTest` | ✅ 通過 |
| inventory-service | `DistributedLockTest` | ✅ 通過 |
| inventory-service | `RetryMechanismPropertyTest` | ❌ 5 項失敗（TEST-BUG-001）|
| auth-service | （無測試） | ⚪ TEST-BUG-002 |
| observability-tests | `GrafanaDashboardConfigurationTest` | ✅ 通過（修復路徑後）|
| observability-tests | `AutoInstrumentationIntegrityPropertyTest` | ✅ 通過 |
| observability-tests | `SamplingStrategyConsistencyPropertyTest` | ✅ 通過 |
| observability-tests | `LgtmStackConnectivityPropertyTest` | ✅ 通過 |
| observability-tests | `BatchProcessingEfficiencyPropertyTest` | ✅ 通過 |
| observability-tests | `TraceDataIntegrityPropertyTest` | ✅ 通過 |
| observability-tests | `ServiceReconnectionRecoveryPropertyTest` | ✅ 通過 |
| observability-tests | `DataRetentionPolicyPropertyTest` | ✅ 通過 |
| observability-tests | `ErrorTraceAccuracyPropertyTest` | ✅ 通過 |
| observability-tests | `SelfMonitoringMetricsPropertyTest` | ✅ 通過 |
| observability-tests | `ConfigurationFaultTolerancePropertyTest` | ❌ 4 errors（TEST-BUG-003）|
| observability-tests | `DynamicConfigurationResponsivenessPropertyTest` | ❌ 5 errors（TEST-BUG-003）|
| observability-tests | `ConfigurationEnvironmentAdaptabilityPropertyTest` | ❌ 3 errors（TEST-BUG-004）|
| observability-tests | `DataPrivacyProtectionPropertyTest` | ❌ 4 failures（TEST-BUG-005）|
| observability-tests | `LogLevelFilteringPropertyTest` | ❌ 1 failure（TEST-BUG-006）|
| observability-tests | `NonBlockingExportPropertyTest` | ❌ 2 failures（TEST-BUG-006）|
| observability-tests | `StructuredLogIntegrityPropertyTest` | ❌ 2 failures（TEST-BUG-006）|
| observability-tests | `LogTraceCorrelationPropertyTest` | ❌ 2 failures（TEST-BUG-007）|
