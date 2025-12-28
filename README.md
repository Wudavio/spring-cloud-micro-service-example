# 微服務訂單庫存系統 (Microservices Order Inventory System)

基於 Spring Cloud 的微服務系統，包含產品服務、庫存服務和訂單服務，支援高並發防超賣機制和完整的購物車功能。

## 系統架構

- **Eureka Server** (8761) - 服務註冊與發現
- **Config Server** (8888) - 配置管理中心
- **API Gateway** (8080) - API 閘道器
- **Product Service** (8081) - 產品管理服務
- **Inventory Service** (8082) - 庫存管理服務
- **Order Service** (8083) - 訂單管理服務（包含購物車功能）

### 訂單服務特色功能

- **購物車管理**: 支援添加、修改、移除商品，自動計算總價
- **訂單處理**: 從購物車一鍵下單，支援訂單狀態管理
- **庫存整合**: 與庫存服務整合，確保庫存充足才能下單
- **產品驗證**: 與產品服務整合，驗證商品資訊和價格
- **事務保證**: 使用分散式事務確保資料一致性
- **完整 API 文檔**: 提供 Swagger UI 介面，支援線上測試

## 技術棧

- **框架**: Spring Boot 3.2.0, Spring Cloud 2023.0.0
- **資料庫**: MySQL 8.0
- **快取**: Redis 7
- **服務發現**: Eureka
- **API 閘道器**: Spring Cloud Gateway
- **API 文檔**: SpringDoc OpenAPI 3 (Swagger UI)
- **分散式追蹤**: Zipkin
- **測試**: JUnit 5, jqwik (Property-Based Testing), Testcontainers

## 快速開始

### 1. 啟動基礎設施

```bash
# 啟動 MySQL, Redis, Zipkin
docker-compose up -d

# 或者手動安裝並啟動服務
# MySQL: localhost:3306 (root/password)
# Redis: localhost:6379
# Zipkin: localhost:9411
```

### 2. 編譯專案

```bash
mvn clean compile
```

### 3. 啟動服務 (按順序)

```bash
# 1. 啟動 Eureka Server
cd eureka-server
mvn spring-boot:run

# 2. 啟動 Config Server
cd ../config-server
mvn spring-boot:run

# 3. 啟動 API Gateway
cd ../api-gateway
mvn spring-boot:run

# 4. 啟動業務服務 (可並行)
cd ../product-service
mvn spring-boot:run

cd ../inventory-service
mvn spring-boot:run

cd ../order-service
mvn spring-boot:run
```

### 4. 驗證服務

- Eureka Dashboard: http://localhost:8761
- Config Server: http://localhost:8888/actuator/health
- API Gateway: http://localhost:8080/actuator/health
- Order Service Swagger UI: http://localhost:8083/swagger-ui/index.html
- Zipkin UI: http://localhost:9411

## API 端點

### 通過 API Gateway (http://localhost:8080)

- **產品服務**: `/api/products/**`
- **庫存服務**: `/api/inventory/**`
- **購物車**: `/api/cart/**`
- **訂單服務**: `/api/orders/**`

### API 文檔 (Swagger UI)

每個服務都提供完整的 OpenAPI 3.0 文檔：

- **訂單服務 API 文檔**: http://localhost:8083/swagger-ui/index.html
  - OpenAPI JSON: http://localhost:8083/v3/api-docs
  - 包含購物車管理和訂單管理的完整 API 文檔
  - 支援線上測試和 API 探索

### 主要 API 功能

#### 購物車管理 API
- `POST /api/cart/items` - 添加商品到購物車
- `PUT /api/cart/items/{itemId}` - 更新購物車項目數量
- `DELETE /api/cart/items/{itemId}` - 從購物車移除商品
- `GET /api/cart` - 獲取購物車詳情
- `DELETE /api/cart` - 清空購物車
- `GET /api/cart/exists` - 檢查購物車是否存在

#### 訂單管理 API
- `POST /api/orders` - 下單（從購物車創建訂單）
- `GET /api/orders/{orderId}` - 根據ID獲取訂單
- `GET /api/orders/number/{orderNumber}` - 根據訂單號獲取訂單
- `GET /api/orders` - 獲取客戶訂單列表（支援分頁）
- `PUT /api/orders/{orderId}/cancel` - 取消訂單
- `PUT /api/orders/{orderId}/status` - 更新訂單狀態
- `GET /api/orders/exists` - 檢查訂單號是否存在

## 測試

```bash
# 執行所有測試
mvn test

# 執行特定服務測試
cd product-service
mvn test

# 執行屬性測試 (Property-Based Tests)
mvn test -Dtest="*PropertyTest"
```

## 開發指南

### 專案結構

```
order-inventory-system/
├── eureka-server/          # 服務註冊中心
├── config-server/          # 配置中心
├── api-gateway/            # API 閘道器
├── product-service/        # 產品服務
├── inventory-service/      # 庫存服務
├── order-service/          # 訂單服務
├── docker-compose.yml      # 基礎設施
└── pom.xml                # 根 POM
```

### 配置管理

所有服務配置都集中在 `config-server/src/main/resources/config-repo/` 目錄下：

- `application.yml` - 通用配置
- `{service-name}.yml` - 服務特定配置

### 資料庫

系統使用三個獨立的資料庫：
- `product_db` - 產品服務
- `inventory_db` - 庫存服務  
- `order_db` - 訂單服務

### 分散式鎖

庫存服務使用 Redis 實現分散式鎖，防止高並發情況下的超賣問題。

## 監控與追蹤

- **健康檢查**: 每個服務的 `/actuator/health` 端點
- **指標監控**: `/actuator/metrics` 端點
- **分散式追蹤**: Zipkin UI (http://localhost:9411)

## 故障排除

### 常見問題

1. **服務無法註冊到 Eureka**
   - 確保 Eureka Server 已啟動
   - 檢查網路連接和防火牆設置

2. **配置無法載入**
   - 確保 Config Server 已啟動
   - 檢查配置檔案路徑和格式

3. **資料庫連接失敗**
   - 確保 MySQL 已啟動並可訪問
   - 檢查資料庫連接配置

4. **Redis 連接失敗**
   - 確保 Redis 已啟動
   - 檢查 Redis 連接配置

### 日誌查看

```bash
# 查看服務日誌
docker-compose logs mysql
docker-compose logs redis

# 查看應用日誌
tail -f {service}/logs/application.log
```

## 貢獻指南

1. Fork 專案
2. 創建功能分支
3. 提交變更
4. 推送到分支
5. 創建 Pull Request

## 許可證

MIT License