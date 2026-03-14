# OpenTelemetry 配置管理系統

這個配置管理系統提供了統一的方式來管理不同環境和服務的 OpenTelemetry 配置。

## 目錄結構

```
config-management/
├── README.md                    # 本檔案
├── otel-config-manager.sh       # 主要的配置管理工具
├── generate-configs.sh          # 配置生成器
├── validate-configs.sh          # 配置驗證工具
├── environments/                # 環境特定配置
│   ├── development.env          # 開發環境配置
│   ├── testing.env              # 測試環境配置
│   ├── staging.env              # 預發布環境配置
│   └── production.env           # 生產環境配置
├── services/                    # 服務特定配置模板
│   ├── api-gateway.template     # API Gateway 配置模板
│   ├── auth-service.template    # Auth Service 配置模板
│   ├── config-server.template   # Config Server 配置模板
│   ├── eureka-server.template   # Eureka Server 配置模板
│   ├── inventory-service.template # Inventory Service 配置模板
│   ├── order-service.template   # Order Service 配置模板
│   └── product-service.template # Product Service 配置模板
└── generated-configs/           # 生成的配置檔案 (自動創建)
```

## 快速開始

### 1. 生成配置檔案

```bash
# 生成開發環境所有服務的配置
./generate-configs.sh development

# 生成生產環境特定服務的配置
./generate-configs.sh production product-service

# 清理並重新生成配置
./generate-configs.sh --clean development
```

### 2. 驗證配置檔案

```bash
# 驗證所有生成的配置檔案
./validate-configs.sh

# 驗證特定配置檔案
./validate-configs.sh product-service-development.env
```

### 3. 使用配置管理工具

```bash
# 列出支援的服務
./otel-config-manager.sh list-services

# 列出支援的環境
./otel-config-manager.sh list-environments

# 生成特定服務和環境的配置
./otel-config-manager.sh generate-env product-service development

# 驗證配置檔案
./otel-config-manager.sh validate-config ../product-service.env

# 更新所有服務的配置
./otel-config-manager.sh update-all production
```

## 配置系統設計

### 環境配置

每個環境都有自己的配置檔案，包含該環境特定的設定：

- **development.env**: 開發環境，100% 取樣，詳細日誌，本地端點
- **testing.env**: 測試環境，50% 取樣，適中日誌，測試端點
- **staging.env**: 預發布環境，10% 取樣，基本日誌，預發布端點
- **production.env**: 生產環境，1% 取樣，錯誤日誌，生產端點

### 服務模板

每個服務都有自己的配置模板，包含該服務特定的儀表化設定：

- **API Gateway**: Spring Cloud Gateway、Reactor Netty、路由追蹤
- **Auth Service**: Spring Security、JWT、敏感資料過濾
- **Inventory Service**: Redis、分散式鎖、排程任務
- **Order Service**: OpenFeign、重試機制、事務管理
- **Product Service**: 事件發布、異步處理
- **Config Server**: Spring Cloud Config
- **Eureka Server**: 服務發現

### 配置合併邏輯

最終的配置檔案由以下部分組合而成：

1. **服務模板**: 服務特定的儀表化配置
2. **環境配置**: 環境特定的設定 (端點、取樣率等)
3. **通用配置**: 所有服務共用的基本配置

## 配置項說明

### 必要配置項

- `OTEL_SERVICE_NAME`: 服務名稱
- `OTEL_SERVICE_VERSION`: 服務版本
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry Collector 端點
- `OTEL_EXPORTER_OTLP_PROTOCOL`: 傳輸協議 (grpc/http)
- `OTEL_TRACES_EXPORTER`: 追蹤導出器
- `OTEL_METRICS_EXPORTER`: 指標導出器
- `OTEL_LOGS_EXPORTER`: 日誌導出器
- `OTEL_RESOURCE_ATTRIBUTES`: 資源屬性
- `JAVA_TOOL_OPTIONS`: Java Agent 配置

### 環境特定配置

- `OTEL_TRACES_SAMPLER_ARG`: 取樣率 (0.0-1.0)
- `OTEL_METRIC_EXPORT_INTERVAL`: 指標導出間隔 (毫秒)
- `OTEL_JAVAAGENT_DEBUG`: 除錯模式
- `OTEL_LOG_LEVEL`: 日誌級別

### 服務特定配置

- `OTEL_INSTRUMENTATION_*_ENABLED`: 各種儀表化開關
- `OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING`: 對等服務映射
- `OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_*`: HTTP 標頭捕獲配置

## 使用範例

### 開發環境設定

```bash
# 1. 生成開發環境配置
./generate-configs.sh development

# 2. 驗證配置
./validate-configs.sh

# 3. 複製到服務目錄
cp generated-configs/product-service-development.env ../product-service.env
```

### 生產環境部署

```bash
# 1. 生成生產環境配置
./generate-configs.sh production --service-version 2.1.0

# 2. 驗證配置
./validate-configs.sh

# 3. 部署到容器
# 在 docker-compose.yml 中使用:
# env_file:
#   - otel-agent/config-management/generated-configs/product-service-production.env
```

### 自定義配置

如果需要為新服務創建配置模板：

1. 在 `services/` 目錄下創建 `new-service.template`
2. 定義服務特定的儀表化配置
3. 使用 `generate-configs.sh` 生成配置

如果需要新的環境：

1. 在 `environments/` 目錄下創建 `new-environment.env`
2. 定義環境特定的設定
3. 使用 `generate-configs.sh` 生成配置

## 最佳實踐

### 1. 配置版本控制

- 將環境配置和服務模板納入版本控制
- 不要將生成的配置檔案納入版本控制
- 使用 CI/CD 流程自動生成和部署配置

### 2. 安全性考量

- 在生產環境中使用較低的取樣率
- 避免在追蹤中包含敏感資訊
- 使用 TLS 加密傳輸遙測數據

### 3. 效能優化

- 根據環境調整批次大小和導出間隔
- 在生產環境中關閉除錯模式
- 監控 OpenTelemetry 對應用程式效能的影響

### 4. 監控和維護

- 定期驗證配置檔案的正確性
- 監控遙測數據的品質和完整性
- 根據實際使用情況調整配置參數

## 故障排除

### 常見問題

1. **配置檔案驗證失敗**
   - 檢查必要配置項是否存在
   - 驗證端點格式和取樣率範圍
   - 確認服務名稱格式正確

2. **生成的配置不正確**
   - 檢查環境配置和服務模板
   - 確認變數替換是否正確
   - 驗證模板檔案語法

3. **服務無法連接到 Collector**
   - 檢查 OTLP 端點配置
   - 確認網路連通性
   - 驗證協議設定 (grpc/http)

### 除錯技巧

1. 使用 `--verbose` 選項獲取詳細輸出
2. 檢查生成的配置檔案內容
3. 使用驗證工具檢查配置正確性
4. 查看應用程式日誌中的 OpenTelemetry 訊息

## 進階功能

### 動態配置更新

配置管理系統支援運行時配置更新：

```bash
# 更新特定服務的配置
./otel-config-manager.sh generate-env product-service production --service-version 2.2.0

# 重新載入配置 (需要應用程式支援)
# 發送 SIGHUP 信號或使用管理端點
```

### 批次操作

```bash
# 批次生成所有環境的配置
for env in development testing staging production; do
    ./generate-configs.sh $env
done

# 批次驗證所有配置
./validate-configs.sh
```

### 配置範本化

可以使用環境變數來自定義配置：

```bash
export SERVICE_VERSION="2.1.0"
export CUSTOM_ENDPOINT="http://custom-collector:4317"
./generate-configs.sh production
```

## 支援和貢獻

如果遇到問題或需要新功能，請：

1. 檢查現有的配置模板和環境設定
2. 查看故障排除指南
3. 提交 Issue 或 Pull Request

## 版本歷史

- v1.0.0: 初始版本，支援基本的配置管理
- v1.1.0: 新增配置驗證功能
- v1.2.0: 支援動態配置更新
- v1.3.0: 新增批次操作和範本化功能