# 專案改進建議

> 分析日期：2026-03-14

## 🔴 嚴重問題（影響功能）

### 1. LGTM 資料不會持久化到 Docker Volume（重大 Bug）

**問題描述：**
Docker Compose 掛載了 `tempo_data:/data/tempo`、`loki_data:/data/loki`、`mimir_data:/data/mimir`，但三個服務的設定檔案都使用 `/tmp/` 路徑存儲資料：

| 服務 | Volume 掛載點 | 設定檔實際路徑 |
|------|-------------|--------------|
| Tempo | `/data/tempo` | `/tmp/tempo/traces` (wal, blocks) |
| Loki | `/data/loki` | `/tmp/loki` (chunks, wal, rules) |
| Mimir | `/data/mimir` | `/tmp/mimir` (blocks, tsdb, compactor) |

**後果：** 每次重啟容器，所有歷史追蹤、日誌、指標資料都會消失。

**修復方式：**
- `tempo/tempo.yaml`：將所有 `/tmp/tempo` 路徑改為 `/data/tempo`
- `loki/loki.yaml`：將 `path_prefix: /tmp/loki` 改為 `/data/loki`
- `mimir/mimir.yaml`：將所有 `/tmp/mimir` 路徑改為 `/data/mimir`

---

### 2. eureka-server 的 OTel Endpoint 使用 HTTPS 但 certs/ 目錄為空

**問題描述：**
`docker-compose.yml` 中 `eureka-server` 設定了 `OTEL_EXPORTER_OTLP_ENDPOINT=https://otel-collector:4327` 且指向 TLS 憑證：
```yaml
- OTEL_EXPORTER_OTLP_CERTIFICATE=/certs/eureka-server-cert.pem
- OTEL_EXPORTER_OTLP_CLIENT_KEY=/certs/eureka-server-key.pem
```
但 `certs/` 目錄是空的，TLS 憑證從未產生。

**後果：** eureka-server 完全無法連到 OTel Collector，追蹤資料送不出去，且啟動時可能報錯。

**修復方式：**
選項 A（快速修復）：將 eureka-server 的 endpoint 改回 `http://otel-collector:4327`（與其他服務一致）。
選項 B（正確做法）：執行 `otel-agent/generate-tls-certificates.sh` 產生憑證，並配置 `otel-collector-config.yaml` 的 receiver 啟用 TLS。

---

### 3. OTel Collector 取樣率問題（已部分修復）

**已修復：**
- ✅ 移除 otel-collector 的 `probabilistic_sampler`（原本在 Collector 層再砍 85%）
- ✅ 將 docker-compose 所有服務的 `OTEL_TRACES_SAMPLER_ARG` 從 `0.1` 改為 `1.0`

**仍需注意：**
生產環境部署前，需將取樣率調回較低值（建議 0.05~0.1），否則高流量下 Tempo 會承受巨大壓力。

---

### 4. OTel Collector 日誌未送到 Loki（已修復）

**已修復：** ✅ `otel-collector-config.yaml` 的 logs pipeline 已加入 `loki` exporter。

---

## 🟡 重要問題（影響穩定性或可維護性）

### 5. Zipkin 與 Tempo 重複追蹤

**問題描述：**
系統同時運行 Zipkin (9411) 和 Tempo (3200) 作為分散式追蹤後端：
- 服務透過 Spring Boot Actuator 送資料到 Zipkin
- 同時透過 OTel Agent 送追蹤到 Tempo

**建議：** 確認是否需要保留 Zipkin。如果 LGTM 堆疊運作正常，可逐步移除 Zipkin 以降低資源消耗。若需保留相容性，在 README 中明確說明兩者的用途。

---

### 6. Grafana Datasource UID 硬編碼可能失效

**問題描述：**
`grafana/provisioning/datasources/datasources.yaml` 中 Tempo 的 `tracesToLogs` 設定：
```yaml
tracesToLogs:
  datasourceUid: loki
```
此處 `datasourceUid: loki` 使用字串而非實際的 UID。Grafana 自動 provisioning 時會指派隨機 UID，如果 `loki` 字串與實際 UID 不符，從 Trace 跳轉到 Logs 的功能會失效。

**修復方式：** 在 Loki datasource 設定中加入明確的 `uid: loki`：
```yaml
- name: Loki
  uid: loki
  type: loki
  ...
```
同樣為 Mimir 加入 `uid: mimir`，Tempo 加入 `uid: tempo`。

---

### 7. 缺少明確的 Docker 網路配置

**問題描述：**
docker-compose.yml 沒有定義 networks，所有服務都在 Docker 預設網路中。這意味著：
- 無法做服務隔離（例如 LGTM 堆疊和微服務之間）
- 難以進行網路安全分區

**建議：** 加入明確的 network 定義：
```yaml
networks:
  microservices-net:
  observability-net:
```

---

### 8. OTel Collector TLS 配置不一致

**問題描述：**
`docker-compose.yml` 中 otel-collector 設定了大量 TLS 環境變數（`TLS_ENABLED=true` 等），但 `otel-collector-config.yaml` 的 receiver 並未實際配置 TLS，只是普通的 gRPC 監聽。這些環境變數對 otel-collector 沒有任何作用（otel-collector 不讀這些自訂變數）。

**建議：** 要麼：
1. 在 otel-collector-config.yaml receiver 中加入 tls 設定（需要先解決 certs 問題）
2. 清除 docker-compose 中無效的 TLS 環境變數

---

## 🟢 優化建議（提升品質）

### 9. 生產環境取樣率應可設定

目前取樣率是 hardcode 在 docker-compose.yml 中。建議透過 `.env` 檔案管理：
```bash
# .env
OTEL_SAMPLING_RATE=1.0  # 開發環境 100%
# OTEL_SAMPLING_RATE=0.05  # 生產環境 5%
```

---

### 10. LGTM 堆疊資源限制不完整

只有 `otel-collector` 設定了 `deploy.resources.limits`，但 Grafana、Tempo、Loki、Mimir 都沒有資源限制，在記憶體有限的環境可能導致 OOM。

---

### 11. Tempo 與 Loki 的 gRPC 端口衝突

`tempo.yaml` 設定 `grpc_listen_port: 9095`，`loki.yaml` 也設定 `grpc_listen_port: 9095`。
雖然兩者在不同容器中沒有衝突，但 docker-compose 未 expose 這些 gRPC 端口，未來如需外部連接 gRPC 端口需注意。

---

### 12. 缺少 Alerting 規則與通知設定

`otel-agent/alerting-rules.yaml` 和 `otel-agent/self-monitoring-alerts.yaml` 存在，但未整合到 Grafana provisioning 中。建議將告警規則加入：
```
grafana/provisioning/alerting/
```

---

### 13. logback-spring.xml 需確認 OTel 整合

各服務的 `logback-spring.xml` 存在，需確認是否：
1. 格式化為結構化 JSON 日誌（方便 Loki 解析）
2. 包含 `traceId` 和 `spanId` MDC 欄位（OTel Agent 應自動注入，但需確認）

建議在日誌格式中加入：
```xml
<pattern>{"timestamp":"%d{ISO8601}","level":"%p","service":"${OTEL_SERVICE_NAME}","traceId":"%X{trace_id}","spanId":"%X{span_id}","message":"%msg"}%n</pattern>
```

---

### 14. 缺少 .gitignore 中對 OTel JAR 的忽略規則

`opentelemetry-javaagent.jar` 會被 `copy-otel-files.sh` 複製到每個服務目錄，這些複本（約 100MB+）應加入 `.gitignore`：
```
# 各服務目錄中的 OTel agent 複本
*/opentelemetry-javaagent.jar
*/otel-config.properties
```

---

## 修復優先順序

| 優先級 | 問題 | 預估影響 |
|--------|------|---------|
| P0 | 問題 1：資料不持久化 | 重啟後所有監控資料消失 |
| P0 | 問題 2：eureka-server TLS certs 不存在 | eureka-server 無追蹤資料 |
| P1 | 問題 6：Grafana UID 硬編碼 | Trace→Log 跳轉功能失效 |
| P1 | 問題 8：OTel TLS 不一致 | 配置混亂，難以維護 |
| P2 | 問題 5：Zipkin 重複 | 資源浪費 |
| P2 | 問題 13：logback 結構化 | 日誌查詢體驗差 |
| P3 | 其餘優化建議 | 可依需求排程 |
