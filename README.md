# 微服務訂單庫存系統 (Microservices Order Inventory System)

基於 Spring Cloud 的企業級微服務系統，包含完整的電商業務功能：產品管理、庫存控制、訂單處理、用戶認證等，支援高並發防超賣機制、完整的購物車功能，以及完整的 LGTM 可觀測性堆疊。

## 系統架構

### 核心服務
- **Eureka Server** (8761) - 服務註冊與發現中心
- **Config Server** (8888) - 統一配置管理中心
- **API Gateway** (8080) - 統一入口閘道器，路由轉發與負載均衡
- **Auth Service** (8084) - 用戶認證與授權服務 (JWT)

### 業務服務
- **Product Service** (8081) - 產品管理服務
- **Inventory Service** (8082) - 庫存管理服務（含分散式鎖防超賣）
- **Order Service** (8083) - 訂單管理服務（包含購物車功能）

### 基礎設施
- **PostgreSQL** (5432) - 主資料庫，每個服務獨立資料庫
- **Redis** (6379) - 快取與分散式鎖

### 可觀測性堆疊 (LGTM)
- **Loki** (3100) - 日誌聚合與查詢
- **Grafana** (3000) - 統一視覺化儀表板
- **Tempo** (3200) - 分散式鏈路追蹤存儲
- **Mimir** (9009) - 指標長期存儲 (Prometheus 相容)
- **OpenTelemetry Collector** (4327/4328) - 遙測數據收集與路由
- **Zipkin** (9411) - 相容舊版追蹤配置（逐步遷移至 Tempo）

### 可觀測性架構

```
微服務 (+ OTel Java Agent)
    │
    ▼
OpenTelemetry Collector (:4327 gRPC / :4328 HTTP)
    ├── Traces ──► Tempo (:3200)
    ├── Metrics ─► Mimir (:9009)
    └── Logs ───► Loki (:3100)
                          │
                          ▼
                    Grafana (:3000)
                  (統一查詢與視覺化)
```

### 系統特色功能

#### 🛒 購物車管理
- 支援添加、修改、移除商品，自動計算總價
- Redis 快取提升性能
- 與產品服務整合驗證商品資訊

#### 📦 訂單處理
- 從購物車一鍵下單，完整訂單生命週期管理
- 與庫存服務整合，確保庫存充足才能下單
- 支援訂單狀態追蹤（待付款、已付款、已發貨、已完成、已取消）

#### 🔒 高並發防超賣
- Redis 分散式鎖機制
- 樂觀鎖與悲觀鎖結合
- 庫存扣減原子性操作

#### 🔐 安全認證
- JWT Token 認證機制
- 用戶註冊、登入、權限管理
- API 安全防護

#### 📊 完整可觀測性 (LGTM)
- **零程式碼修改**：OpenTelemetry Java Agent 自動儀表化
- **分散式追蹤**：完整的跨服務 Trace，可在 Grafana 中追蹤完整 Java call stack
- **指標監控**：JVM、HTTP、資料庫連接池等指標自動收集
- **結構化日誌**：含 trace_id/span_id 關聯，可從 log 直接跳轉到對應 trace
- **預建儀表板**：微服務總覽、服務詳情、分散式追蹤、錯誤監控

## 技術棧

### 後端框架
- **Spring Boot**: 3.2.0
- **Spring Cloud**: 2023.0.0
- **Spring Security**: JWT 認證
- **Spring Data JPA**: 資料持久化
- **Spring Cloud Gateway**: API 閘道器
- **Spring Cloud OpenFeign**: 服務間通信

### 資料庫與快取
- **PostgreSQL**: 15-alpine (主資料庫)
- **Redis**: 7-alpine (快取與分散式鎖)

### 服務治理
- **Eureka**: 服務註冊與發現
- **Spring Cloud Config**: 配置管理
- **Spring Boot Actuator**: 健康檢查與監控

### 可觀測性 (LGTM)
- **OpenTelemetry Java Agent**: 自動儀表化，無需修改程式碼
- **OpenTelemetry Collector**: 遙測數據收集、處理與路由
- **Grafana**: 統一視覺化儀表板 (預建 5 個儀表板)
- **Tempo**: 分散式追蹤存儲，支援 TraceQL
- **Loki**: 日誌聚合，支援 LogQL
- **Mimir**: 指標長期存儲，支援 PromQL

### API 文檔
- **SpringDoc OpenAPI 3**: Swagger UI 介面
- **完整 API 文檔**: 支援線上測試與 API 探索

### 測試框架
- **JUnit 5**: 單元測試
- **jqwik**: 屬性測試 (Property-Based Testing)
- **Testcontainers**: 整合測試
- **Spring Boot Test**: 整合測試支援

### 容器化
- **Docker**: 容器化部署
- **Docker Compose**: 多服務編排

## 快速開始

### 前置需求
- **Java 17** 或更高版本
- **Maven 3.8+**
- **Docker** 和 **Docker Compose**

### 方法一：使用 Docker Compose（推薦）

#### 1. 複製 OpenTelemetry Agent 到各服務目錄
```bash
# 必須先執行此步驟，才能建置 Docker 映像
chmod +x copy-otel-files.sh
./copy-otel-files.sh
```

#### 2. 編譯打包所有服務
```bash
# 清理並打包所有微服務（跳過測試以加快速度）
mvn clean package -DskipTests

# 或者包含測試的完整打包
mvn clean package
```

#### 3. 啟動完整系統
```bash
# 啟動所有服務（基礎設施 + LGTM 堆疊 + 微服務）
docker-compose up -d

# 查看服務狀態
docker-compose ps

# 查看服務日誌
docker-compose logs -f [service-name]
```

#### 4. 驗證服務啟動
等待所有服務健康檢查通過（約 3-5 分鐘），然後訪問：

| 服務 | URL | 說明 |
|------|-----|------|
| Eureka | http://localhost:8761 | 服務註冊中心 |
| API Gateway | http://localhost:8080/actuator/health | API 入口 |
| **Grafana** | **http://localhost:3000** | **可觀測性儀表板 (admin/admin)** |
| Tempo | http://localhost:3200/ready | 追蹤存儲 |
| Loki | http://localhost:3100/ready | 日誌存儲 |
| Mimir | http://localhost:9009/ready | 指標存儲 |
| Zipkin | http://localhost:9411 | 舊版追蹤 UI |

### 方法二：本地開發模式

#### 1. 啟動基礎設施 + LGTM 堆疊
```bash
# 啟動基礎設施和可觀測性服務
docker-compose up -d postgres redis otel-collector mimir tempo loki grafana zipkin
```

#### 2. 按順序啟動微服務
```bash
# 1. 啟動服務註冊中心
cd eureka-server && mvn spring-boot:run

# 2. 啟動配置中心
cd ../config-server && mvn spring-boot:run

# 3. 啟動 API 閘道器
cd ../api-gateway && mvn spring-boot:run

# 4. 啟動認證服務
cd ../auth-service && mvn spring-boot:run

# 5. 啟動業務服務（可並行啟動）
cd ../product-service && mvn spring-boot:run &
cd ../inventory-service && mvn spring-boot:run &
cd ../order-service && mvn spring-boot:run &
```

### 停止服務
```bash
# 停止所有 Docker 服務
docker-compose down

# 停止並清理資料卷（注意：會刪除資料庫和監控資料）
docker-compose down -v
```

## Grafana 儀表板使用指南

### 登入 Grafana
1. 開啟 http://localhost:3000
2. 帳號：`admin` / 密碼：`admin`

### 預建儀表板

| 儀表板 | 說明 |
|--------|------|
| **微服務總覽** | 所有服務的 HTTP 請求速率、延遲、錯誤率 |
| **服務詳情** | 單一服務的詳細指標（JVM、DB 連接池、Redis） |
| **分散式追蹤** | 跨服務追蹤視覺化 |
| **錯誤監控** | 錯誤率趨勢與詳細錯誤日誌 |
| **OTel Collector 自監控** | Collector 效能與健康狀況 |

### 追蹤 Java Call Stack

1. 在 Grafana 左側選單點擊 **Explore**
2. 選擇資料源 **Tempo**
3. 點擊 **Search** 標籤
4. 選擇 Service Name（如 `order-service`）
5. 點擊 **Run query** 查看最近的 traces
6. 點擊任一 trace 展開完整的 span 樹，可看到：
   - HTTP 請求進入 API Gateway
   - 轉發至業務服務
   - 資料庫查詢 (SQL 語句)
   - 服務間 Feign 調用
   - Redis 操作

### 從日誌跳轉到 Trace

1. 在 Grafana Explore 中選擇 **Loki** 資料源
2. 查詢服務日誌，例如：`{service="order-service"}`
3. 點擊含有 `trace_id` 的日誌行
4. 點擊 **View Trace** 直接跳轉到對應的 Tempo trace

## API 文檔與測試

### 🎯 統一 Swagger UI 入口（推薦）

**通過 API Gateway 統一訪問所有服務的 API 文檔**：
- **統一 Swagger UI**: http://localhost:8080/swagger-ui.html

在 Swagger UI 界面中，你可以通過下拉選單選擇不同的服務：
- **API Gateway** - 閘道器本身的 API
- **Auth Service** - 用戶認證與授權 API
- **Product Service** - 產品管理 API
- **Inventory Service** - 庫存管理 API
- **Order Service** - 訂單與購物車 API

### 📋 各服務 API 文檔端點

#### 通過 API Gateway 訪問（推薦）
- **API Gateway 本身**: http://localhost:8080/v3/api-docs
- **認證服務**: http://localhost:8080/v3/api-docs/auth-service
- **產品服務**: http://localhost:8080/v3/api-docs/product-service
- **庫存服務**: http://localhost:8080/v3/api-docs/inventory-service
- **訂單服務**: http://localhost:8080/v3/api-docs/order-service

#### 直接訪問各服務（開發調試用）
- **產品服務 API**: http://localhost:8081/swagger-ui.html
- **庫存服務 API**: http://localhost:8082/swagger-ui.html
- **訂單服務 API**: http://localhost:8083/swagger-ui.html
- **認證服務 API**: http://localhost:8084/swagger-ui.html

### API 端點總覽

#### 通過 API Gateway (http://localhost:8080)
- **認證相關**: `/api/auth/**`
  - `POST /api/auth/register` - 用戶註冊
  - `POST /api/auth/login` - 用戶登入
  - `POST /api/auth/refresh` - 刷新 Token
  - `GET /api/auth/profile` - 獲取用戶資料

- **產品管理**: `/api/products/**`
  - `GET /api/products` - 獲取產品列表（支援分頁）
  - `GET /api/products/{id}` - 根據 ID 獲取產品
  - `POST /api/products` - 創建產品
  - `PUT /api/products/{id}` - 更新產品
  - `DELETE /api/products/{id}` - 刪除產品

- **庫存管理**: `/api/inventory/**`
  - `GET /api/inventory/{productId}` - 獲取產品庫存
  - `POST /api/inventory/reserve` - 預留庫存
  - `POST /api/inventory/release` - 釋放庫存
  - `PUT /api/inventory/{productId}` - 更新庫存數量

- **購物車管理**: `/api/cart/**`
  - `POST /api/cart/items` - 添加商品到購物車
  - `PUT /api/cart/items/{itemId}` - 更新購物車項目數量
  - `DELETE /api/cart/items/{itemId}` - 從購物車移除商品
  - `GET /api/cart` - 獲取購物車詳情
  - `DELETE /api/cart` - 清空購物車

- **訂單管理**: `/api/orders/**`
  - `POST /api/orders` - 創建訂單（從購物車）
  - `GET /api/orders/{orderId}` - 根據 ID 獲取訂單
  - `GET /api/orders` - 獲取用戶訂單列表（支援分頁）
  - `PUT /api/orders/{orderId}/cancel` - 取消訂單
  - `PUT /api/orders/{orderId}/status` - 更新訂單狀態

### 使用 Swagger UI 測試 API

1. **訪問 Swagger UI**: http://localhost:8080/swagger-ui/index.html
2. **認證流程**:
   - 先調用 `/api/auth/register` 註冊用戶
   - 調用 `/api/auth/login` 獲取 JWT Token
   - 點擊頁面右上角 "Authorize" 按鈕
   - 輸入 `Bearer {your-jwt-token}` 進行認證
3. **測試業務流程**:
   - 創建產品 → 設置庫存 → 添加到購物車 → 創建訂單

### API 測試範例

#### 用戶註冊
```bash
curl -X POST "http://localhost:8080/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'
```

#### 用戶登入
```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

#### 創建產品（需要認證）
```bash
curl -X POST "http://localhost:8080/api/products" \
  -H "Authorization: Bearer {your-jwt-token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "iPhone 15",
    "description": "最新款 iPhone",
    "price": 999.99,
    "category": "Electronics"
  }'
```

## 資料庫架構

系統使用 PostgreSQL 作為主資料庫，每個微服務擁有獨立的資料庫實例：

### 資料庫配置
- **PostgreSQL**: 15-alpine
- **連接埠**: 5432
- **主資料庫**: postgres
- **認證**: postgres/password

### 服務資料庫分離
- **product_db**: 產品服務專用資料庫
  - 用戶: product_user / product_pass
  - 表: products, categories

- **inventory_db**: 庫存服務專用資料庫
  - 用戶: inventory_user / inventory_pass
  - 表: inventory, inventory_transactions

- **order_db**: 訂單服務專用資料庫
  - 用戶: order_user / order_pass
  - 表: orders, order_items, shopping_carts, cart_items

- **auth_db**: 認證服務專用資料庫
  - 用戶: auth_user / auth_pass
  - 表: users, roles, user_roles

### 資料庫初始化
資料庫和用戶會在 Docker 容器啟動時自動創建，初始化腳本位於 `init-databases.sql`。

## 測試

### 執行測試
```bash
# 執行所有測試
mvn test

# 執行特定服務測試
cd order-service && mvn test

# 執行屬性測試 (Property-Based Tests)
mvn test -Dtest="*PropertyTest"

# 執行整合測試
mvn test -Dtest="*IntegrationTest"

# 跳過測試的快速打包
mvn clean package -DskipTests
```

### 測試類型
- **單元測試**: 使用 JUnit 5
- **屬性測試**: 使用 jqwik 進行 Property-Based Testing
- **整合測試**: 使用 Testcontainers 進行資料庫整合測試
- **API 測試**: 使用 Spring Boot Test 進行 REST API 測試

## 監控與可觀測性

### Grafana 儀表板
- **Grafana UI**: http://localhost:3000 (admin/admin)
- 預建 5 個儀表板，涵蓋微服務總覽、追蹤分析、錯誤監控

### 分散式追蹤
- **Tempo (via Grafana)**: http://localhost:3000 → Explore → Tempo
- **Zipkin UI (舊版)**: http://localhost:9411
- 完整跨服務追蹤，支援 Java call stack 視覺化

### 日誌查詢
- **Loki (via Grafana)**: http://localhost:3000 → Explore → Loki
- 結構化日誌，含 trace_id 關聯，可從日誌一鍵跳轉到對應 trace
- 查詢範例：`{service_name="order-service"} |= "ERROR"`

### 指標監控
- **Mimir (via Grafana)**: http://localhost:3000 → Explore → Mimir
- JVM、HTTP、資料庫連接池等指標
- 查詢範例：`http_server_request_duration_seconds_count{service_name="product-service"}`

### 服務健康檢查端點
每個服務都提供 Spring Boot Actuator 端點：
- **健康狀態**: `http://localhost:{port}/actuator/health`
- **服務資訊**: `http://localhost:{port}/actuator/info`
- **指標監控**: `http://localhost:{port}/actuator/metrics`

### OTel Collector 狀態頁
- **健康檢查**: http://localhost:13133/health
- **內部狀態 (zPages)**: http://localhost:55679/debug/tracez
- **效能分析 (pprof)**: http://localhost:1777/debug/pprof

## 開發指南

### 專案結構
```
microservices-order-inventory-system/
├── eureka-server/              # 服務註冊與發現中心
├── config-server/              # 統一配置管理中心
├── api-gateway/                # API 閘道器與路由
├── auth-service/               # 用戶認證與授權服務
├── product-service/            # 產品管理服務
├── inventory-service/          # 庫存管理服務（含分散式鎖）
├── order-service/              # 訂單與購物車服務
├── grafana/                    # Grafana 配置與預建儀表板
│   ├── provisioning/           # 自動配置資料源和儀表板
│   └── dashboards/             # 預建儀表板 JSON
├── loki/                       # Loki 日誌存儲配置
├── tempo/                      # Tempo 追蹤存儲配置
├── mimir/                      # Mimir 指標存儲配置
├── otel-agent/                 # OpenTelemetry Java Agent 和配置
├── otel-collector-config.yaml  # OTel Collector 管道配置
├── copy-otel-files.sh          # 複製 OTel Agent 到各服務目錄
├── docker-compose.yml          # Docker 編排配置（含 LGTM 堆疊）
├── init-databases.sql          # 資料庫初始化腳本
└── pom.xml                     # Maven 父專案配置
```

### 配置管理
所有服務配置都集中在 Config Server 管理：
- **配置路徑**: `config-server/src/main/resources/config-repo/`
- **通用配置**: `application.yml`
- **服務特定配置**: `{service-name}.yml`
- **環境配置**: `{service-name}-{profile}.yml`

### 服務間通信
- **同步通信**: 使用 OpenFeign 進行 REST API 調用
- **服務發現**: 通過 Eureka 自動發現服務實例
- **負載均衡**: Spring Cloud LoadBalancer 自動負載均衡
- **熔斷器**: 集成 Resilience4j 提供熔斷保護

### OpenTelemetry 配置
每個服務透過以下方式啟用自動儀表化：
- **Dockerfile**: 使用 `-javaagent:/app/opentelemetry-javaagent.jar` 啟動
- **環境變數**: 透過 docker-compose.yml 注入 OTEL 配置
- **otel-config.properties**: 服務層面的細節配置

關鍵環境變數：
```yaml
OTEL_SERVICE_NAME: 服務名稱（識別追蹤來源）
OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4327
OTEL_EXPORTER_OTLP_PROTOCOL: grpc
OTEL_TRACES_SAMPLER: traceidratio
OTEL_TRACES_SAMPLER_ARG: 1.0  # 100% 取樣（開發環境）
```

### 分散式鎖實現
庫存服務使用 Redis 實現分散式鎖，防止高並發超賣：
```java
@Component
public class DistributedLockService {
    // Redis 分散式鎖實現
    // 支援鎖超時、自動釋放等機制
}
```

### 安全機制
- **JWT 認證**: 無狀態 Token 認證
- **角色權限**: 基於角色的訪問控制 (RBAC)
- **API 保護**: 所有業務 API 需要認證
- **密碼加密**: BCrypt 加密存儲

### 開發最佳實踐

#### 1. 服務開發規範
- 每個服務獨立資料庫
- 使用 DTO 進行資料傳輸
- 統一異常處理
- 完整的 API 文檔

#### 2. 資料庫設計
- 遵循資料庫正規化原則
- 適當的索引設計
- 外鍵約束與資料完整性
- 軟刪除機制

#### 3. 快取策略
- Redis 快取熱點資料
- 快取過期策略
- 快取穿透防護
- 分散式鎖機制

#### 4. 測試策略
- 單元測試覆蓋率 > 80%
- 整合測試使用 Testcontainers
- 屬性測試驗證業務邏輯
- API 測試確保介面正確性

## 部署指南

### Docker 部署（生產環境）

#### 1. 複製 OTel Agent 並構建所有服務映像
```bash
# 複製 OpenTelemetry Java Agent 到各服務目錄
./copy-otel-files.sh

# 打包所有服務
mvn clean package -DskipTests

# 構建 Docker 映像
docker-compose build

# 或者單獨構建特定服務
docker-compose build order-service
```

#### 2. 生產環境部署
```bash
# 啟動所有服務（含 LGTM 堆疊）
docker-compose up -d

# 檢查服務狀態
docker-compose ps

# 查看服務健康狀態
curl http://localhost:8080/actuator/health
```

#### 3. 擴展服務實例
```bash
# 擴展訂單服務到 3 個實例
docker-compose up -d --scale order-service=3

# 擴展產品服務到 2 個實例
docker-compose up -d --scale product-service=2
```

### Kubernetes 部署
```yaml
# 範例 Kubernetes 部署配置
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
      - name: order-service
        image: order-service:latest
        ports:
        - containerPort: 8083
```

### 環境變數配置
```bash
# 資料庫配置
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/order_db
SPRING_DATASOURCE_USERNAME=order_user
SPRING_DATASOURCE_PASSWORD=order_pass

# Redis 配置
REDIS_HOST=redis
REDIS_PORT=6379

# 服務發現配置
EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/

# JWT 配置
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000

# OTel 配置
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4327
OTEL_TRACES_SAMPLER_ARG=0.1  # 生產環境建議降低取樣率
```

## 故障排除

### 常見問題與解決方案

#### 1. 服務啟動問題

**問題**: 服務無法註冊到 Eureka
```bash
# 解決步驟
1. 確保 Eureka Server 已啟動並可訪問
curl http://localhost:8761/actuator/health

2. 檢查服務配置中的 Eureka 地址
# application.yml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

3. 檢查網路連接和防火牆設置
```

**問題**: 配置無法載入
```bash
# 解決步驟
1. 確保 Config Server 已啟動
curl http://localhost:8888/actuator/health

2. 檢查配置檔案路徑和格式
ls config-server/src/main/resources/config-repo/

3. 驗證配置是否正確載入
curl http://localhost:8888/{service-name}/default
```

#### 2. Grafana 追蹤看不到資料

**問題**: Tempo 中沒有 trace 資料
```bash
# 解決步驟
1. 確認 OTel Collector 健康
curl http://localhost:13133/health

2. 確認 Tempo 健康
curl http://localhost:3200/ready

3. 確認取樣率設定 (OTEL_TRACES_SAMPLER_ARG 應為 1.0 用於開發)

4. 透過 zPages 確認 Collector 是否接收到 spans
http://localhost:55679/debug/tracez
```

**問題**: Loki 中看不到日誌
```bash
# 確認 Loki 健康
curl http://localhost:3100/ready

# 在 Grafana Explore 中查詢
# 資料源: Loki
# 查詢: {service_name="order-service"}
```

#### 3. 資料庫連接問題

**問題**: 資料庫連接失敗
```bash
# 解決步驟
1. 確保 PostgreSQL 已啟動
docker-compose ps postgres

2. 檢查資料庫連接配置
docker-compose logs postgres

3. 驗證資料庫和用戶是否創建成功
docker exec -it postgres-db psql -U postgres -c "\l"
```

**問題**: 資料庫權限不足
```bash
# 解決方案
docker exec -it postgres-db psql -U postgres -d order_db -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO order_user;"
```

#### 4. Redis 連接問題

**問題**: Redis 連接失敗
```bash
# 解決步驟
1. 確保 Redis 已啟動
docker-compose ps redis

2. 測試 Redis 連接
docker exec -it redis-cache redis-cli ping

3. 檢查 Redis 配置
docker-compose logs redis
```

#### 5. API 調用問題

**問題**: 401 未授權錯誤
```bash
# 解決步驟
1. 確保已獲取有效的 JWT Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password123"}'

2. 在請求頭中包含 Authorization
curl -H "Authorization: Bearer {your-jwt-token}" \
  http://localhost:8080/api/products
```

### 日誌查看與分析

#### Docker 環境日誌
```bash
# 查看所有服務日誌
docker-compose logs

# 查看特定服務日誌
docker-compose logs -f order-service

# 查看最近的錯誤日誌
docker-compose logs --tail=50 order-service | grep ERROR

# 實時監控日誌
docker-compose logs -f --tail=100
```

#### 透過 Grafana / Loki 查詢日誌
```
# 查詢特定服務錯誤
{service_name="order-service"} |= "ERROR"

# 查詢包含特定 trace_id 的日誌
{service_name="product-service"} |= "abc123def456"

# 查詢所有服務的錯誤
{service_namespace="microservices"} | json | level = "ERROR"
```

### 性能調優

#### JVM 調優參數
```bash
# Dockerfile 中添加 JVM 參數
ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-XX:+UseG1GC", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

#### 資料庫連接池調優
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

#### Redis 連接調優
```yaml
# application.yml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20
        max-idle: 8
        min-idle: 2
        max-wait: -1ms
```

#### OTel 取樣率調整（生產環境）
```yaml
# 生產環境建議降低取樣率以減少負載
OTEL_TRACES_SAMPLER: traceidratio
OTEL_TRACES_SAMPLER_ARG: 0.1  # 10% 取樣
```

### 監控告警

#### 健康檢查腳本
```bash
#!/bin/bash
# health-check.sh
services=("eureka-server:8761" "api-gateway:8080" "order-service:8083" "grafana:3000" "tempo:3200" "loki:3100")

for service in "${services[@]}"; do
    IFS=':' read -r name port <<< "$service"
    if curl -f "http://localhost:$port/actuator/health" > /dev/null 2>&1 || \
       curl -f "http://localhost:$port/ready" > /dev/null 2>&1 || \
       curl -f "http://localhost:$port/api/health" > /dev/null 2>&1; then
        echo "✅ $name is healthy"
    else
        echo "❌ $name is unhealthy"
    fi
done
```

#### 服務重啟腳本
```bash
#!/bin/bash
# restart-service.sh
SERVICE_NAME=$1
echo "Restarting $SERVICE_NAME..."
docker-compose restart $SERVICE_NAME
echo "Waiting for service to be healthy..."
sleep 30
curl -f "http://localhost:8080/actuator/health" && echo "✅ Service is back online"
```

## 貢獻指南

### 開發流程
1. **Fork 專案** 到你的 GitHub 帳號
2. **創建功能分支** `git checkout -b feature/new-feature`
3. **提交變更** `git commit -am 'Add new feature'`
4. **推送分支** `git push origin feature/new-feature`
5. **創建 Pull Request** 並描述變更內容

### 程式碼規範
- 遵循 Java 程式碼規範
- 使用有意義的變數和方法名稱
- 添加適當的註釋和文檔
- 確保測試覆蓋率 > 80%

### 提交規範
```bash
# 提交訊息格式
<type>(<scope>): <description>

# 範例
feat(order): add shopping cart functionality
fix(inventory): resolve concurrent update issue
docs(readme): update API documentation
test(product): add integration tests
feat(observability): add LGTM stack integration
```

## 許可證

本專案採用 MIT 許可證 - 詳見 [LICENSE](LICENSE) 檔案

## 聯絡資訊

- **專案維護者**: [Your Name]
- **Email**: [your.email@example.com]
- **GitHub**: [https://github.com/yourusername/microservices-order-inventory-system]

---

## 更新日誌

### v1.2.0 (2026-03-14)
- 📊 整合完整 LGTM 可觀測性堆疊 (Loki, Grafana, Tempo, Mimir)
- 🔭 OpenTelemetry Java Agent 自動儀表化所有服務
- 📈 預建 5 個 Grafana 監控儀表板
- 🔗 日誌與追蹤關聯（從 Loki 日誌跳轉至 Tempo trace）
- ⚡ OpenTelemetry Collector 批次處理與背壓控制

### v1.1.0 (2025-01-01)
- 🔍 整合 Zipkin 分散式鏈路追蹤
- 🚪 實作 API Gateway 安全機制與 Swagger 聚合
- 🔐 完善 Auth Service JWT 認證機制

### v1.0.0 (2024-12-31)
- ✨ 初始版本發布
- 🚀 完整的微服務架構
- 🔐 JWT 認證機制
- 🛒 購物車與訂單功能
- 📚 完整的 API 文檔
- 🐳 Docker 容器化部署
- 🧪 完整的測試覆蓋
