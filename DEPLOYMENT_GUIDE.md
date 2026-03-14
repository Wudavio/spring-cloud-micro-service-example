# OpenTelemetry LGTM 整合部署指南

## 概覽

本指南提供了在微服務系統中部署 OpenTelemetry 和 LGTM (Loki, Grafana, Tempo, Mimir) 可觀測性堆疊的完整說明。

## 系統架構

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   微服務層      │    │   遙測收集層    │    │   LGTM 堆疊     │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ Product Service │    │ OpenTelemetry   │    │ Mimir (指標)   │
│ Order Service   │───▶│ Java Agent      │───▶│ Tempo (追蹤)   │
│ Inventory Svc   │    │                 │    │ Loki (日誌)    │
│ Auth Service    │    │ OpenTelemetry   │    │ Grafana (視覺) │
│ API Gateway     │    │ Collector       │    │                 │
│ Eureka Server   │    │                 │    │                 │
│ Config Server   │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 前置需求

### 系統需求
- Docker 和 Docker Compose
- Java 17 或更高版本
- Maven 3.6 或更高版本
- 至少 4GB RAM
- 至少 10GB 可用磁碟空間

### 網路需求
- 確保以下端口可用：
  - 3000: Grafana Web UI
  - 3100: Loki API
  - 3200: Tempo API
  - 4317/4318: OpenTelemetry Collector OTLP
  - 8888: OpenTelemetry Collector 自監控
  - 9009: Mimir API
  - 8761: Eureka Server
  - 8080: API Gateway
  - 8081-8084: 微服務端口

## 部署步驟

### 1. 準備環境

```bash
# 克隆專案
git clone <repository-url>
cd spring-cloud-micro-service-example

# 確保 Docker 正在運行
docker --version
docker-compose --version
```

### 2. 配置 API 金鑰（可選）

如果需要啟用 API 金鑰驗證：

```bash
# 創建 API 金鑰目錄
mkdir -p api-keys

# 生成 API 金鑰
./otel-agent/api-key-management.sh generate-all

# 檢查生成的金鑰
ls -la api-keys/
```

### 3. 配置 TLS 憑證（可選）

如果需要啟用 TLS 加密：

```bash
# 生成 TLS 憑證
./otel-agent/generate-tls-certificates.sh

# 檢查生成的憑證
ls -la certs/
```

### 4. 編譯微服務

```bash
# 編譯所有微服務
mvn clean package -DskipTests

# 或者編譯單個服務
cd eureka-server && mvn clean package -DskipTests
cd ../config-server && mvn clean package -DskipTests
# ... 重複其他服務
```

### 5. 啟動 LGTM 堆疊

```bash
# 啟動基礎設施服務（資料庫、快取、LGTM）
docker-compose up -d postgres redis mimir tempo loki grafana otel-collector

# 檢查服務狀態
docker-compose ps

# 等待服務啟動完成
sleep 30
```

### 6. 啟動微服務

```bash
# 按順序啟動微服務
docker-compose up -d eureka-server
sleep 30

docker-compose up -d config-server
sleep 30

docker-compose up -d auth-service product-service inventory-service
sleep 30

docker-compose up -d order-service api-gateway
```

### 7. 驗證部署

```bash
# 檢查所有服務狀態
docker-compose ps

# 檢查服務健康狀況
curl http://localhost:8761/actuator/health  # Eureka
curl http://localhost:8080/actuator/health  # API Gateway
curl http://localhost:3000/api/health       # Grafana
curl http://localhost:13133/health          # OpenTelemetry Collector
```

## 配置說明

### OpenTelemetry Java Agent 配置

每個微服務都配置了以下 OpenTelemetry 環境變數：

```yaml
environment:
  # 服務識別
  - OTEL_SERVICE_NAME=service-name
  - OTEL_RESOURCE_ATTRIBUTES=service.name=service-name,service.version=1.0.0,deployment.environment=production
  
  # 導出配置
  - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4327
  - OTEL_EXPORTER_OTLP_PROTOCOL=grpc
  - OTEL_EXPORTER_OTLP_TIMEOUT=30000
  - OTEL_EXPORTER_OTLP_COMPRESSION=gzip
  
  # 批次處理配置
  - OTEL_BSP_SCHEDULE_DELAY=500
  - OTEL_BSP_MAX_QUEUE_SIZE=2048
  - OTEL_BSP_MAX_EXPORT_BATCH_SIZE=512
  - OTEL_BSP_EXPORT_TIMEOUT=30000
  
  # 取樣配置
  - OTEL_TRACES_SAMPLER=traceidratio
  - OTEL_TRACES_SAMPLER_ARG=0.1
  
  # 重試配置
  - OTEL_EXPORTER_OTLP_RETRY_ENABLED=true
  - OTEL_EXPORTER_OTLP_RETRY_INITIAL_BACKOFF=1s
  - OTEL_EXPORTER_OTLP_RETRY_MAX_BACKOFF=5s
  - OTEL_EXPORTER_OTLP_RETRY_MAX_ATTEMPTS=3
```

### OpenTelemetry Collector 配置

Collector 配置位於 `otel-collector-config.yaml`：

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 1s
    send_batch_size: 1024
  resource:
    attributes:
      - key: environment
        value: production
        action: upsert

exporters:
  prometheusremotewrite:
    endpoint: http://mimir:9009/api/v1/push
  otlp/tempo:
    endpoint: http://tempo:4317
    tls:
      insecure: true
  loki:
    endpoint: http://loki:3100/loki/api/v1/push

service:
  pipelines:
    metrics:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [prometheusremotewrite]
    traces:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [otlp/tempo]
    logs:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [loki]
```

## 監控和儀表板

### 訪問 Grafana

1. 開啟瀏覽器訪問 http://localhost:3000
2. 使用預設登入資訊：
   - 使用者名稱: `admin`
   - 密碼: `admin`

### 預配置的儀表板

系統包含以下預配置的儀表板：

1. **微服務概覽** (`microservices-overview.json`)
   - 所有服務的健康狀況
   - 關鍵效能指標
   - 錯誤率和響應時間

2. **服務詳細資訊** (`service-details.json`)
   - 單個服務的詳細指標
   - JVM 指標
   - HTTP 請求統計

3. **分散式追蹤** (`distributed-tracing.json`)
   - 請求追蹤視覺化
   - 服務依賴關係
   - 效能瓶頸分析

4. **錯誤監控** (`error-monitoring.json`)
   - 錯誤檢測和告警
   - 異常趨勢分析
   - 系統健康狀況

5. **OpenTelemetry 自監控** (`otel-collector-self-monitoring.json`)
   - Collector 效能指標
   - 資料處理統計
   - 系統資源使用

### 資料源配置

Grafana 自動配置以下資料源：

- **Mimir**: http://mimir:9009 (指標)
- **Tempo**: http://tempo:3200 (追蹤)
- **Loki**: http://loki:3100 (日誌)

## 效能調優

### 記憶體配置

根據系統負載調整 JVM 記憶體設定：

```yaml
# 在 docker-compose.yml 中調整
environment:
  - JAVA_OPTS=-Xms512m -Xmx1024m
```

### OpenTelemetry 取樣率

根據需要調整取樣率：

```yaml
# 高流量環境建議較低取樣率
- OTEL_TRACES_SAMPLER_ARG=0.01  # 1%

# 開發環境可使用較高取樣率
- OTEL_TRACES_SAMPLER_ARG=1.0   # 100%
```

### 批次處理調優

根據系統負載調整批次處理參數：

```yaml
# 高流量環境
- OTEL_BSP_MAX_QUEUE_SIZE=4096
- OTEL_BSP_MAX_EXPORT_BATCH_SIZE=1024

# 低延遲環境
- OTEL_BSP_SCHEDULE_DELAY=100
- OTEL_BSP_MAX_EXPORT_BATCH_SIZE=256
```

## 安全配置

### 啟用 TLS

1. 生成憑證：
```bash
./otel-agent/generate-tls-certificates.sh
```

2. 更新 docker-compose.yml 中的 TLS 配置：
```yaml
environment:
  - TLS_ENABLED=true
  - OTEL_EXPORTER_OTLP_ENDPOINT=https://otel-collector:4327
```

### 啟用 API 金鑰驗證

1. 生成 API 金鑰：
```bash
./otel-agent/api-key-management.sh generate-all
```

2. 配置服務使用 API 金鑰：
```yaml
environment:
  - OTEL_EXPORTER_OTLP_HEADERS=authorization=Bearer $(cat /api-keys/service-api-key.txt)
```

## 資料保留策略

### 配置資料保留期限

編輯 `otel-agent/data-retention-config.yaml`：

```yaml
retention:
  metrics: 30d      # 指標保留 30 天
  traces: 7d        # 追蹤保留 7 天
  logs: 14d         # 日誌保留 14 天
```

### 啟用自動清理

```bash
# 設定定時清理任務
crontab -e

# 添加以下行（每天凌晨 2 點執行清理）
0 2 * * * /path/to/otel-agent/data-retention-cron.sh
```

## 備份和恢復

### 備份配置

```bash
# 備份 Grafana 儀表板
docker exec grafana grafana-cli admin export-dashboard > backup/dashboards.json

# 備份 LGTM 配置
cp -r grafana/ backup/
cp -r mimir/ backup/
cp -r tempo/ backup/
cp -r loki/ backup/
```

### 恢復配置

```bash
# 恢復配置檔案
cp -r backup/grafana/ .
cp -r backup/mimir/ .
cp -r backup/tempo/ .
cp -r backup/loki/ .

# 重啟服務
docker-compose restart grafana mimir tempo loki
```

## 升級指南

### 升級 OpenTelemetry Java Agent

1. 下載新版本：
```bash
wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
```

2. 更新所有服務的 JAR 檔案：
```bash
./copy-otel-files.sh
```

3. 重新編譯和部署：
```bash
mvn clean package -DskipTests
docker-compose up --build -d
```

### 升級 LGTM 堆疊

1. 更新 docker-compose.yml 中的映像版本
2. 重新啟動服務：
```bash
docker-compose pull
docker-compose up -d
```

## 下一步

部署完成後，建議：

1. 查看 [操作指南](OPERATIONS_GUIDE.md) 了解日常維護
2. 查看 [故障排除指南](TROUBLESHOOTING_GUIDE.md) 了解常見問題解決方案
3. 設定告警和通知
4. 定期檢查系統效能和資源使用情況