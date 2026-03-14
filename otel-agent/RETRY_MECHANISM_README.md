# OpenTelemetry 重試機制配置指南

## 概覽

本文檔說明 OpenTelemetry Collector 和微服務中實作的重試機制，用於確保遙測數據在網路故障或後端服務暫時不可用時能夠可靠地導出。

## 重試機制配置

### OpenTelemetry Collector 配置

#### 1. Prometheus Remote Write 導出器（指標到 Mimir）

```yaml
prometheusremotewrite:
  endpoint: http://mimir:9009/api/v1/push
  timeout: 30s
  retry_on_failure:
    enabled: true
    initial_interval: 1s        # 初始重試間隔
    max_interval: 60s           # 最大重試間隔
    max_elapsed_time: 600s      # 最大重試時間
    randomization_factor: 0.5   # 隨機化因子
    multiplier: 2.0             # 間隔倍增因子
    max_retry_attempts: 10      # 最大重試次數
  sending_queue:
    enabled: true
    num_consumers: 10           # 消費者數量
    queue_size: 5000           # 佇列大小
```

#### 2. OTLP 導出器（追蹤到 Tempo）

```yaml
otlp/tempo:
  endpoint: http://tempo:3200
  retry_on_failure:
    enabled: true
    initial_interval: 1s
    max_interval: 60s
    max_elapsed_time: 600s
    randomization_factor: 0.5
    multiplier: 2.0
    max_retry_attempts: 10
  sending_queue:
    enabled: true
    num_consumers: 10
    queue_size: 5000
```

#### 3. Loki 導出器（日誌到 Loki）

```yaml
loki:
  endpoint: http://loki:3100/loki/api/v1/push
  retry_on_failure:
    enabled: true
    initial_interval: 1s
    max_interval: 60s
    max_elapsed_time: 600s
    randomization_factor: 0.5
    multiplier: 2.0
    max_retry_attempts: 10
  sending_queue:
    enabled: true
    num_consumers: 5
    queue_size: 2000
```

### 微服務配置

每個微服務的 `otel-config.properties` 檔案包含以下重試相關配置：

```properties
# 批次處理配置
otel.bsp.max.export.batch.size=512
otel.bsp.export.timeout=30000
otel.bsp.schedule.delay=5000

# 指標批次處理
otel.metric.export.batch.size=512
otel.metric.export.batch.timeout=30000
otel.metric.export.interval=15000

# 壓縮配置
otel.exporter.otlp.compression=gzip
otel.exporter.otlp.timeout=30000
```

## 重試策略說明

### 指數退避算法

重試機制使用指數退避算法，具有以下特點：

1. **初始間隔**: 1 秒
2. **倍增因子**: 2.0（每次重試間隔翻倍）
3. **最大間隔**: 60 秒
4. **隨機化**: 0.5 的隨機化因子避免雷群效應
5. **最大時間**: 600 秒（10 分鐘）後停止重試
6. **最大次數**: 最多重試 10 次

### 重試間隔計算

```
重試間隔 = min(max_interval, initial_interval * (multiplier ^ attempt_number) * (1 ± randomization_factor))
```

例如：
- 第 1 次重試: ~1 秒
- 第 2 次重試: ~2 秒  
- 第 3 次重試: ~4 秒
- 第 4 次重試: ~8 秒
- 第 5 次重試: ~16 秒
- 第 6 次重試: ~32 秒
- 第 7+ 次重試: ~60 秒（達到最大間隔）

## 發送佇列機制

### 佇列配置

- **指標佇列**: 5000 個項目，10 個消費者
- **追蹤佇列**: 5000 個項目，10 個消費者  
- **日誌佇列**: 2000 個項目，5 個消費者

### 佇列行為

1. **正常情況**: 數據直接發送到後端
2. **網路故障**: 數據暫存在佇列中
3. **佇列滿載**: 新數據被丟棄（避免記憶體溢出）
4. **服務恢復**: 佇列中的數據按順序重新發送

## 監控和告警

### 關鍵指標

1. **導出成功率**:
   - `otelcol_exporter_sent_spans_total`
   - `otelcol_exporter_sent_metric_points_total`
   - `otelcol_exporter_sent_log_records_total`

2. **導出失敗率**:
   - `otelcol_exporter_send_failed_spans_total`
   - `otelcol_exporter_send_failed_metric_points_total`
   - `otelcol_exporter_send_failed_log_records_total`

3. **重試次數**:
   - `otelcol_exporter_retry_total`

4. **佇列狀態**:
   - `otelcol_exporter_queue_size`
   - `otelcol_exporter_queue_capacity`

### 告警規則

參考 `alerting-rules.yaml` 檔案中的告警配置：

- 導出失敗率 > 10%
- 重試率 > 5 次/秒
- 佇列使用率 > 80%
- 記憶體使用 > 500MB

## 測試和驗證

### 監控腳本

使用 `monitor-retry-mechanism.sh` 腳本監控重試機制：

```bash
# 基本監控
./monitor-retry-mechanism.sh

# 包含重試測試
./monitor-retry-mechanism.sh --test-retry
```

### 測試腳本

使用 `test-retry-mechanism.sh` 腳本測試重試機制：

```bash
# 測試所有重試機制
./test-retry-mechanism.sh all

# 僅測試指標重試
./test-retry-mechanism.sh metrics

# 僅測試追蹤重試  
./test-retry-mechanism.sh traces

# 僅測試佇列行為
./test-retry-mechanism.sh queue
```

**注意**: 網路故障模擬需要 root 權限。

## 故障排除

### 常見問題

1. **重試次數過多**
   - 檢查後端服務狀態
   - 調整重試參數
   - 檢查網路連接

2. **佇列滿載**
   - 增加佇列大小
   - 增加消費者數量
   - 檢查後端處理能力

3. **記憶體使用過高**
   - 啟用記憶體限制器
   - 調整批次大小
   - 減少佇列大小

### 日誌檢查

檢查以下日誌檔案：

- `/tmp/otel-collector.log` - Collector 一般日誌
- `/tmp/otel-collector-errors.log` - Collector 錯誤日誌
- 各微服務的應用程式日誌

### 配置調優

根據環境調整以下參數：

1. **網路環境良好**:
   - 減少 `max_retry_attempts`
   - 減少 `max_elapsed_time`
   - 增加 `initial_interval`

2. **網路環境不穩定**:
   - 增加 `max_retry_attempts`
   - 增加 `max_elapsed_time`
   - 增加佇列大小

3. **高負載環境**:
   - 增加消費者數量
   - 增加批次大小
   - 啟用壓縮

## 最佳實踐

1. **監控設置**:
   - 設置適當的告警閾值
   - 定期檢查重試指標
   - 監控佇列使用情況

2. **配置管理**:
   - 根據環境調整重試參數
   - 定期檢查配置檔案
   - 測試故障恢復流程

3. **效能優化**:
   - 啟用壓縮減少網路負載
   - 使用適當的批次大小
   - 調整消費者數量

4. **安全考慮**:
   - 使用 TLS 加密傳輸
   - 配置適當的超時值
   - 避免敏感資訊洩露

## 相關檔案

- `otel-collector-config.yaml` - Collector 主配置
- `*/otel-config.properties` - 微服務配置
- `alerting-rules.yaml` - 告警規則
- `monitor-retry-mechanism.sh` - 監控腳本
- `test-retry-mechanism.sh` - 測試腳本