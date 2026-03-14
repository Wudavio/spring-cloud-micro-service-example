# OpenTelemetry Java Agent 配置

此目錄包含了 OpenTelemetry Java Agent 的配置檔案和環境變數設定。

## 檔案說明

### opentelemetry-javaagent.jar
最新版本的 OpenTelemetry Java Agent JAR 檔案，用於自動儀表化 Java 應用程式。

### otel-config.properties
統一的 OpenTelemetry 配置檔案，包含：
- 儀表化設定
- 導出器配置
- 效能調優參數
- 安全性設定

### 環境變數檔案
每個微服務都有對應的環境變數檔案：
- `product-service.env` - Product Service 配置
- `order-service.env` - Order Service 配置
- `inventory-service.env` - Inventory Service 配置
- `auth-service.env` - Auth Service 配置
- `api-gateway.env` - API Gateway 配置
- `eureka-server.env` - Eureka Server 配置
- `config-server.env` - Config Server 配置

### otel-env-template.env
環境變數模板檔案，可用於創建新服務的配置。

## 使用方式

1. 在 Dockerfile 中複製 JAR 檔案和配置檔案
2. 設定環境變數或使用 env_file
3. 確保 Java 應用程式啟動時載入 Java Agent

## 配置說明

### 關鍵環境變數
- `OTEL_SERVICE_NAME`: 服務名稱
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry Collector 端點
- `OTEL_TRACES_SAMPLER_ARG`: 追蹤取樣率 (0.0-1.0)
- `JAVA_TOOL_OPTIONS`: Java Agent 載入參數

### 效能調優
- `otel.bsp.max.export.batch.size`: 批次導出大小
- `otel.bsp.export.timeout`: 導出超時時間
- `otel.metric.export.interval`: 指標導出間隔

### 安全性
- 所有數據傳輸使用 gzip 壓縮
- 支援 TLS 加密（需在 Collector 端配置）
- 可配置 API 金鑰身份驗證

## 注意事項

1. 確保 OpenTelemetry Collector 在服務啟動前已經運行
2. 根據環境調整取樣率以平衡效能和可觀測性
3. 在生產環境中關閉除錯模式
4. 定期更新 OpenTelemetry Java Agent 到最新版本