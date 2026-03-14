# Bug 記錄

> 記錄日期：2026-03-14
> 記錄環境：Docker Compose 本機開發環境

---

## BUG-001：Tempo / Loki 容器在 Docker 健康檢查中持續顯示 unhealthy

**嚴重程度：** 🟡 中（功能正常，但狀態顯示誤導）

**狀態：** 開啟

**症狀：**
```
tempo    Up 6 minutes (unhealthy)
loki     Up 6 minutes (unhealthy)
```
但實際上兩個服務都在正常接收與提供資料：
- Tempo `/api/search` 可查到 trace 資料
- Loki 日誌聚合正常啟動

**Root Cause：**
Tempo 和 Loki 的 `/ready` endpoint 在 Ingester 啟動後有 15 秒的強制等待期（`waiting for 15s after being ready`），這是 single-instance 模式下 memberlist ring 的設計行為。Docker healthcheck 每 30 秒檢查一次，前幾次都得到非 200 回應，進入 unhealthy 狀態後並未自動恢復（因為 `/ready` 的回應持續是非 200）。

**相關日誌：**
```
Ingester not ready: ingester check ready failed: waiting for 15s after being ready  (Tempo)
Ingester not ready: waiting for 15s after being ready  (Loki)
```

**修復建議：**
修改 docker-compose.yml 中 tempo 和 loki 的 healthcheck，改用 `/status/services` 或延長 `start_period`：
```yaml
# 方案 A：延長 start_period 讓 ingester 有時間完成初始化
healthcheck:
  test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:3200/ready"]
  interval: 30s
  timeout: 10s
  retries: 10
  start_period: 60s   # 從 30s 改為 60s

# 方案 B：改用 metrics 端點確認（更可靠）
healthcheck:
  test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:3200/metrics"]
  interval: 30s
  timeout: 10s
  retries: 5
  start_period: 30s
```

---

## BUG-002：OTel Collector 容器 health check 顯示 unhealthy，但服務運作正常

**嚴重程度：** 🟡 中（功能正常，但狀態顯示誤導）

**狀態：** 開啟

**症狀：**
```
otel-collector   Up 6 minutes (unhealthy)
```
但 OTel Collector 實際健康狀態：
```json
{"status":"Server available","upSince":"2026-03-14T15:14:11.903487677Z","uptime":"6m22s"}
```
且 Tempo 已有 trace 資料，代表 Collector 有在正常轉發。

**Root Cause：**
Docker Compose 在 healthcheck 中設定的是 `http://localhost:13133/health`，但 otel-collector-config.yaml 的 health_check extension 設定是：
```yaml
health_check:
  endpoint: 0.0.0.0:13133
  path: /health
```
實際上 Collector 回傳 200 OK，但 Docker 把它標為 unhealthy 可能是因為健康檢查在 Collector 完全初始化所有 pipeline 前就執行了，而 `start_period: 30s` 不夠長。

**修復建議：**
延長 `start_period` 到 60s，或增加 retries：
```yaml
healthcheck:
  test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:13133/health"]
  interval: 30s
  timeout: 10s
  retries: 10
  start_period: 60s
```

---

## BUG-003：Mimir 容器持續顯示 unhealthy（健康檢查需更長預熱時間）

**嚴重程度：** 🟡 中（功能正常）

**狀態：** 開啟

**症狀：**
```
mimir   Up 8 minutes (unhealthy)
```
但直接查詢 `/ready` 顯示正常：
```
ready
```
Grafana Mimir 資料源可正常查詢指標。

**Root Cause：**
Mimir 作為指標存儲，Ingester 初始化需要較長時間。docker-compose 的 healthcheck `start_period: 30s` 不足以覆蓋 Mimir 所有元件的啟動時間。

**修復建議：**
延長 Mimir 的 `start_period` 到 90s：
```yaml
healthcheck:
  test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:9009/ready"]
  interval: 30s
  timeout: 10s
  retries: 5
  start_period: 90s
```

---

## BUG-004：OTel Collector loki exporter 不存在於新版本 collector-contrib

**嚴重程度：** 🔴 高（初始啟動時完全 crash，已修復）

**狀態：** 已修復 ✅

**症狀：**
otel-collector 啟動後立即 crash（Exit Code 1）：
```
'exporters' unknown type: "loki" for id: "loki"
(valid values: [... otlphttp ... debug ...])
```

**Root Cause：**
`otel-collector-contrib` 新版本（0.142.0）移除了獨立的 `loki` exporter。Loki 3.x 原生支援 OTLP，應改用 `otlphttp` exporter 推送到 Loki 的 `/otlp` endpoint。

**修復方式：**
`otel-collector-config.yaml` 中將：
```yaml
# 舊（已移除）
loki:
  endpoint: http://loki:3100/loki/api/v1/push
```
改為：
```yaml
# 新（使用 Loki OTLP 原生接收）
otlphttp/loki:
  endpoint: http://loki:3100/otlp
  tls:
    insecure: true
```
並同步更新 logs pipeline：
```yaml
logs:
  exporters: [otlphttp/loki]  # 原為 [loki]
```

---

## BUG-005：Tempo / Loki 資料寫入 /tmp 而非持久化 Volume

**嚴重程度：** 🔴 高（重啟後所有追蹤和日誌資料遺失，已修復）

**狀態：** 已修復 ✅

**症狀：**
重啟 tempo/loki/mimir 容器後，所有歷史資料消失。

**Root Cause：**
docker-compose.yml 將 volume 掛載到：
- `tempo_data:/data/tempo`
- `loki_data:/data/loki`
- `mimir_data:/data/mimir`

但設定檔使用 `/tmp/` 路徑：
```yaml
# tempo.yaml  (修復前)
storage.trace.local.path: /tmp/tempo/traces
storage.trace.wal.path:   /tmp/tempo/wal

# loki.yaml  (修復前)
common.path_prefix: /tmp/loki

# mimir.yaml  (修復前)
blocks_storage.filesystem.dir: /tmp/mimir/blocks
```
`/tmp` 是容器內的臨時目錄，不對應到掛載的 volume。

**修復方式：**
將三個設定檔中所有 `/tmp/tempo`、`/tmp/loki`、`/tmp/mimir` 路徑改為 `/data/tempo`、`/data/loki`、`/data/mimir`。

---

## BUG-006：Tempo / Loki 因 Volume 目錄權限不足而 crash

**嚴重程度：** 🔴 高（初始啟動時完全 crash，已修復）

**狀態：** 已修復 ✅

**症狀：**
Tempo 和 Loki 啟動後立即 crash（Exit Code 1）：
```
# Tempo
mkdir /data/tempo/traces: permission denied

# Loki
mkdir /data/loki/rules: permission denied
```

**Root Cause：**
Docker volume 新建時目錄屬於 root，但 Tempo（UID 10001）和 Loki（UID 10001）容器以非 root 用戶執行，沒有在 `/data/` 建立子目錄的權限。

**修復方式：**
docker-compose.yml 中為 tempo 和 loki 加上 `user: "0"`（以 root 執行），適用於本機開發環境：
```yaml
tempo:
  user: "0"
  ...
loki:
  user: "0"
  ...
```
**注意：** 生產環境應改用 init container 或 `chmod` 設定正確的目錄權限，而非直接以 root 執行。

---

## BUG-007：eureka-server OTel Endpoint 使用 HTTPS 但 TLS 憑證不存在

**嚴重程度：** 🔴 高（eureka-server 無法送出 trace 資料，已修復）

**狀態：** 已修復 ✅

**症狀：**
eureka-server 啟動後 OTel agent 無法連線到 Collector，trace 資料完全遺失。

**Root Cause：**
docker-compose.yml 的 eureka-server 設定了：
```yaml
OTEL_EXPORTER_OTLP_ENDPOINT=https://otel-collector:4327
OTEL_EXPORTER_OTLP_CERTIFICATE=/certs/eureka-server-cert.pem
OTEL_EXPORTER_OTLP_CLIENT_KEY=/certs/eureka-server-key.pem
```
但 `certs/` 目錄為空，TLS 憑證從未產生，且 otel-collector 也未設定 TLS。其他服務均使用 `http://`。

**修復方式：**
將 eureka-server 的 endpoint 改回 `http://`，移除無效的 TLS 設定：
```yaml
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4327
```

---

## BUG-008：OTel Collector docker-compose 設定有大量無效的 TLS/API 金鑰環境變數

**嚴重程度：** 🟠 低（不影響功能，但設定混亂）

**狀態：** 已修復 ✅

**症狀：**
docker-compose.yml 的 otel-collector 設定了 15 個環境變數（TLS 路徑、API 金鑰路徑），但 otel-collector 程式本身不讀取這些自訂環境變數，且 `certs/` 目錄為空，`$$(cat /api-keys/xxx.txt)` 語法在 Docker Compose 中也不會被 shell 展開。

**Root Cause：**
設計文件規劃了 TLS + API 金鑰驗證功能，但實際實作只完成了 API 金鑰檔案的建立，TLS 憑證和 Collector 的 TLS receiver 設定均未完成，導致這些環境變數無任何作用。

**修復方式：**
移除 otel-collector 中所有 TLS 和 API 金鑰相關的環境變數和 volume 掛載，保留功能性的設定：
```yaml
environment:
  - ENVIRONMENT=development
  - GOMEMLIMIT=512MiB
  - GOMAXPROCS=2
```

---

## BUG-009：auth-service RegisterRequest 缺少 fullName 和 confirmPassword 欄位說明

**嚴重程度：** 🟠 低（API 文件不完整，使用者體驗差）

**狀態：** 開啟

**症狀：**
呼叫 `POST /api/auth/register` 時，若 request body 缺少 `fullName` 或 `confirmPassword` 欄位，回傳 403 Forbidden（應為 400 Bad Request）：
```bash
curl -X POST "http://localhost:8080/api/auth/register" \
  -d '{"username":"test","email":"test@test.com","password":"pass123"}'
# → HTTP 403 (錯誤的狀態碼)
```

**Root Cause 分析：**
兩個問題疊加：
1. RegisterRequest 的必填欄位（`fullName`、`confirmPassword`）未在 API 文件或錯誤訊息中清楚說明
2. `MethodArgumentNotValidException`（Bean Validation 失敗）沒有對應的 `@ExceptionHandler`，由 `DefaultHandlerExceptionResolver` 處理，但回傳了 403 而非 400

**修復建議：**
1. 加入 `@ControllerAdvice` 統一處理 `MethodArgumentNotValidException` 並回傳 400
2. 完整記錄 API 需要的所有欄位

---

## 驗證結果

| 項目 | 狀態 |
|------|------|
| Tempo 接收 trace 資料 | ✅ 確認（查詢到 10+ traces） |
| 各服務 trace 到 Tempo | ✅ auth-service、api-gateway、config-server、order-service |
| OTel Collector 運作 | ✅ 確認（Server available） |
| Mimir 就緒 | ✅ 確認（/ready 回傳 200） |
| Loki 啟動 | ✅ 確認（服務運作中） |
| Grafana 健康 | ✅ healthy |
| 所有微服務健康 | ✅ 全部 healthy |
| LGTM 資料持久化 | ✅ 已修復 volume 路徑 |
