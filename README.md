# 微服務訂單庫存系統

Spring Cloud 電商後端範例：產品、庫存（防超賣）、購物車／訂單、JWT 認證，以及 LGTM 可觀測性（Loki / Grafana / Tempo / Mimir）。

## 前端

對應 Vue 3 前台（私有）：https://github.com/Wudavio/vue3-e  
本機 `pnpm dev` → http://localhost:5173（proxy 至本倉庫 Gateway :8080）

## 架構一覽

| 類型 | 服務 | 埠 |
|------|------|-----|
| 入口 | API Gateway | **8080**（正式對外唯一業務入口） |
| 基礎設施 | Eureka / Config / PostgreSQL / Redis | 8761 / 8888 / 5432 / 6379 |
| 業務 | Auth / Product / Inventory / Order | 內部 8084–8081–8082–8083（Docker 不對外 publish） |
| 可觀測性 | Grafana / Tempo / Loki / Mimir / OTel Collector | 3000 / 3200 / 3100 / 9009 / 4327 |

```
Client → API Gateway (:8080 /api/**)
           ├─ auth / products / inventory / cart / orders
           └─ 服務內部互相呼叫（Eureka + Feign）

App (+ OTel Agent) → OTel Collector → Tempo (traces) / Mimir (metrics) / Loki (logs)
                                              └──────────► Grafana (:3000)
```

## 快速開始（Docker）

**需求：** Java 17、Maven 3.8+、Docker Compose

```bash
# 1. 環境變數（至少設定 JWT_SECRET，≥ 32 bytes）
cp .env.example .env

# 2. OTel agent 複製到各服務（建映像前需要）
# Windows: 若無 bash，可手動把 otel-agent 內 jar/properties 複製到各服務目錄
chmod +x copy-otel-files.sh && ./copy-otel-files.sh

# 3. 打包
mvn clean package -DskipTests

# 4. 啟動
docker compose up -d --build

# 5. 狀態
docker compose ps
```

首次啟動約需數分鐘等健康檢查。停止：`docker compose down`（加 `-v` 會清資料卷）。

## 常用網址

| 用途 | URL |
|------|-----|
| **Swagger（API 文件／試打）** | http://localhost:8080/swagger-ui.html |
| **Grafana**（admin / admin） | http://localhost:3000 |
| Eureka | http://localhost:8761 |
| Gateway 健康 | http://localhost:8080/actuator/health |

Grafana：Explore → **Tempo** 看 trace／stack；**Loki** 看 log；**Mimir** 看指標。  
Tempo 本身無 UI，就緒檢查：http://localhost:3200/ready。

## API 重點

- 前綴一律 **`/api`**，經 Gateway：`http://localhost:8080/api/...`
- 登入：`POST /api/auth/login` → `Authorization: Bearer <JWT>`（回應含 `expiresIn` / `expiresAt`）
- 角色：

| 能力 | CUSTOMER | OPERATOR | ADMIN |
|------|:--------:|:--------:|:-----:|
| 查產品／庫存 | ✓ | ✓ | ✓ |
| 購物車、自己的訂單 | ✓ | ✓ | ✓ |
| 改訂單出貨狀態 | | ✓ | ✓ |
| 產品 CRUD、圖片上傳、庫存調整 | | | ✓ |

- 產品圖片（每產品最多 10 張，JPEG/PNG/WebP/GIF）：  
  `POST /api/products/{id}/images`（multipart 欄位 `files`，需 ADMIN）  
  Docker 持久化：volume `product_uploads` → `/app/uploads/products`
- 統一錯誤：`timestamp / status / code / message / path / traceId / details`  
- 分頁：`content / page / size / totalElements / totalPages / first / last`

### 最小 curl 範例

```bash
# 註冊／登入
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"testuser","password":"password123"}'

# 建產品（需 ADMIN token）
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo","description":"x","price":9.99,"category":"Gadgets"}'

# 上傳圖片
curl -X POST http://localhost:8080/api/products/1/images \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@./a.jpg" -F "files=@./b.png"
```

## 專案目錄（精簡）

```
api-gateway/          統一入口
auth-service/         JWT 認證
product-service/      產品 + 圖片
inventory-service/    庫存與預留
order-service/        購物車與訂單
eureka-server/        服務發現
config-server/        集中設定
common-api/           共用錯誤／分頁契約
grafana/ loki/ tempo/ mimir/   可觀測性設定
docker-compose.yml    整套編排
```

## 測試

```bash
mvn test                          # 全部
mvn -pl product-service test      # 單一模組
mvn clean package -DskipTests     # 只打包
```

## 更多文件

| 文件 | 內容 |
|------|------|
| [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) | 部署細節 |
| [OPERATIONS_GUIDE.md](OPERATIONS_GUIDE.md) | 營運 |
| [TROUBLESHOOTING_GUIDE.md](TROUBLESHOOTING_GUIDE.md) | 故障排除 |
| [IMPROVEMENT_NOTES.md](IMPROVEMENT_NOTES.md) | 已知改善項 |

## 授權

MIT — 見 [LICENSE](LICENSE)（若有）。
