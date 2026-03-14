# Dockerfile 更新說明

## 更新內容

所有微服務的 Dockerfile 已更新以包含 OpenTelemetry Java Agent 整合：

### 更新的服務
- product-service
- order-service
- inventory-service
- auth-service
- api-gateway
- eureka-server
- config-server

### 主要變更

1. **複製 OpenTelemetry 檔案**
   ```dockerfile
   COPY ../otel-agent/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
   COPY ../otel-agent/otel-config.properties otel-config.properties
   ```

2. **設定環境變數**
   - `OTEL_SERVICE_NAME`: 服務名稱
   - `OTEL_SERVICE_VERSION`: 服務版本
   - `OTEL_RESOURCE_ATTRIBUTES`: 資源屬性標籤
   - `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry Collector 端點
   - `OTEL_EXPORTER_OTLP_PROTOCOL`: 通訊協定 (grpc)
   - `OTEL_METRICS_EXPORTER`: 指標導出器
   - `OTEL_TRACES_EXPORTER`: 追蹤導出器
   - `OTEL_LOGS_EXPORTER`: 日誌導出器
   - `OTEL_JAVAAGENT_CONFIGURATION_FILE`: 配置檔案路徑

3. **更新啟動命令**
   ```dockerfile
   ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
   ```

### 建置注意事項

1. **建置順序**
   - 必須先建置 JAR 檔案
   - 確保 otel-agent 目錄存在且包含必要檔案

2. **Docker 建置命令**
   ```bash
   # 建置單個服務
   docker build -t service-name ./service-name/
   
   # 使用 Docker Compose 建置所有服務
   docker-compose build
   ```

3. **檔案路徑**
   - OpenTelemetry JAR 檔案：`/app/opentelemetry-javaagent.jar`
   - 配置檔案：`/app/otel-config.properties`

### 驗證

建置完成後，可以透過以下方式驗證：

1. **檢查容器內檔案**
   ```bash
   docker run --rm -it service-name ls -la /app/
   ```

2. **檢查環境變數**
   ```bash
   docker run --rm -it service-name env | grep OTEL
   ```

3. **檢查 Java Agent 載入**
   - 查看應用程式啟動日誌
   - 確認 OpenTelemetry 初始化訊息

### 故障排除

1. **檔案不存在錯誤**
   - 確認 otel-agent 目錄存在
   - 檢查檔案路徑是否正確

2. **Java Agent 載入失敗**
   - 檢查 JAR 檔案完整性
   - 確認 Java 版本相容性

3. **連接失敗**
   - 確認 OpenTelemetry Collector 正在運行
   - 檢查網路連接和端點配置