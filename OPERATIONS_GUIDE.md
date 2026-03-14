# OpenTelemetry LGTM 整合操作指南

## 概覽

本指南提供了 OpenTelemetry LGTM 可觀測性堆疊的日常操作和維護指導。

## 日常監控

### 系統健康檢查

#### 自動化健康檢查腳本

創建每日健康檢查腳本：

```bash
#!/bin/bash
# daily-health-check.sh

echo "=== OpenTelemetry LGTM 系統健康檢查 ==="
echo "檢查時間: $(date)"
echo

# 檢查 Docker 容器狀態
echo "1. 檢查容器狀態:"
docker-compose ps

echo
echo "2. 檢查服務健康狀況:"

# 檢查各個服務的健康端點
services=(
    "eureka-server:8761"
    "config-server:8888"
    "api-gateway:8080"
    "product-service:8081"
    "inventory-service:8082"
    "order-service:8083"
    "auth-service:8084"
)

for service in "${services[@]}"; do
    name=$(echo $service | cut -d: -f1)
    port=$(echo $service | cut -d: -f2)
    
    if curl -s -f "http://localhost:$port/actuator/health" > /dev/null; then
        echo "✓ $name: 健康"
    else
        echo "✗ $name: 不健康"
    fi
done

echo
echo "3. 檢查 LGTM 堆疊:"

# 檢查 LGTM 服務
lgtm_services=(
    "grafana:3000:/api/health"
    "mimir:9009:/ready"
    "tempo:3200:/ready"
    "loki:3100:/ready"
    "otel-collector:13133:/health"
)

for service in "${lgtm_services[@]}"; do
    name=$(echo $service | cut -d: -f1)
    port=$(echo $service | cut -d: -f2)
    path=$(echo $service | cut -d: -f3)
    
    if curl -s -f "http://localhost:$port$path" > /dev/null; then
        echo "✓ $name: 健康"
    else
        echo "✗ $name: 不健康"
    fi
done

echo
echo "4. 檢查資源使用情況:"
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"

echo
echo "=== 健康檢查完成 ==="
```

#### 設定定時健康檢查

```bash
# 設定 crontab
crontab -e

# 每小時執行健康檢查
0 * * * * /path/to/daily-health-check.sh >> /var/log/otel-health-check.log 2>&1

# 每天早上 8 點發送健康報告
0 8 * * * /path/to/daily-health-check.sh | mail -s "LGTM 系統健康報告" admin@company.com
```

### 關鍵指標監控

#### 必須監控的指標

1. **系統指標**
   - CPU 使用率 < 80%
   - 記憶體使用率 < 85%
   - 磁碟使用率 < 90%
   - 網路延遲 < 100ms

2. **應用指標**
   - HTTP 請求成功率 > 99%
   - 平均響應時間 < 200ms
   - 錯誤率 < 1%
   - 服務可用性 > 99.9%

3. **OpenTelemetry 指標**
   - 遙測數據導出成功率 > 99%
   - Collector 記憶體使用 < 512MB
   - 批次處理延遲 < 1s
   - 重試次數 < 5%

#### Grafana 告警設定

在 Grafana 中設定以下告警規則：

```yaml
# 高錯誤率告警
- alert: HighErrorRate
  expr: rate(http_server_requests_total{status=~"5.."}[5m]) > 0.01
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "服務 {{ $labels.service }} 錯誤率過高"
    description: "錯誤率: {{ $value | humanizePercentage }}"

# 高響應時間告警
- alert: HighResponseTime
  expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 0.2
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "服務 {{ $labels.service }} 響應時間過長"
    description: "95th 百分位響應時間: {{ $value }}s"

# 服務下線告警
- alert: ServiceDown
  expr: up == 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "服務 {{ $labels.instance }} 已下線"
    description: "服務已停止響應超過 1 分鐘"
```

## 日誌管理

### 日誌收集和分析

#### 查看服務日誌

```bash
# 查看特定服務日誌
docker-compose logs -f eureka-server

# 查看所有服務日誌
docker-compose logs -f

# 查看最近的錯誤日誌
docker-compose logs --tail=100 | grep ERROR

# 查看特定時間範圍的日誌
docker-compose logs --since="2024-01-01T00:00:00" --until="2024-01-01T23:59:59"
```

#### 使用 Loki 查詢日誌

在 Grafana 中使用 LogQL 查詢：

```logql
# 查看特定服務的錯誤日誌
{service="product-service"} |= "ERROR"

# 查看包含特定關鍵字的日誌
{service=~".*-service"} |= "OutOfMemoryError"

# 統計錯誤日誌數量
count_over_time({service=~".*-service"} |= "ERROR" [1h])

# 查看特定 trace ID 的日誌
{service=~".*-service"} |= "traceId=abc123"
```

### 日誌輪轉和清理

#### 設定日誌輪轉

```bash
# 創建 logrotate 配置
sudo tee /etc/logrotate.d/otel-lgtm << EOF
/var/log/otel-*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 644 root root
    postrotate
        docker-compose restart otel-collector
    endscript
}
EOF
```

#### 自動清理舊日誌

```bash
#!/bin/bash
# log-cleanup.sh

# 清理 30 天前的日誌檔案
find /var/log -name "otel-*.log.*" -mtime +30 -delete

# 清理 Docker 日誌
docker system prune -f --volumes --filter "until=720h"

echo "日誌清理完成: $(date)"
```

## 效能調優

### 監控效能指標

#### 建立效能基準

```bash
#!/bin/bash
# performance-baseline.sh

echo "=== 建立效能基準 ==="

# 記錄當前資源使用情況
echo "CPU 使用率:"
top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1

echo "記憶體使用率:"
free | grep Mem | awk '{printf "%.2f%%\n", $3/$2 * 100.0}'

echo "磁碟使用率:"
df -h | grep -E '^/dev/' | awk '{print $5}' | head -1

# 測試 API 響應時間
echo "API 響應時間測試:"
for i in {1..10}; do
    curl -w "@curl-format.txt" -o /dev/null -s "http://localhost:8080/actuator/health"
done

echo "=== 基準建立完成 ==="
```

創建 `curl-format.txt`：
```
     time_namelookup:  %{time_namelookup}\n
        time_connect:  %{time_connect}\n
     time_appconnect:  %{time_appconnect}\n
    time_pretransfer:  %{time_pretransfer}\n
       time_redirect:  %{time_redirect}\n
  time_starttransfer:  %{time_starttransfer}\n
                     ----------\n
          time_total:  %{time_total}\n
```

### 調優建議

#### JVM 調優

```yaml
# 在 docker-compose.yml 中調整 JVM 參數
environment:
  - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+PrintGCDetails
```

#### OpenTelemetry 調優

```yaml
# 高流量環境調優
environment:
  # 降低取樣率
  - OTEL_TRACES_SAMPLER_ARG=0.01
  
  # 增加批次大小
  - OTEL_BSP_MAX_EXPORT_BATCH_SIZE=2048
  - OTEL_BSP_MAX_QUEUE_SIZE=8192
  
  # 調整導出間隔
  - OTEL_BSP_SCHEDULE_DELAY=1000
```

#### 資料庫調優

```yaml
# PostgreSQL 調優
postgres:
  environment:
    - POSTGRES_SHARED_PRELOAD_LIBRARIES=pg_stat_statements
    - POSTGRES_MAX_CONNECTIONS=200
    - POSTGRES_SHARED_BUFFERS=256MB
    - POSTGRES_EFFECTIVE_CACHE_SIZE=1GB
```

## 備份和恢復

### 自動備份策略

#### 每日備份腳本

```bash
#!/bin/bash
# daily-backup.sh

BACKUP_DIR="/backup/$(date +%Y%m%d)"
mkdir -p $BACKUP_DIR

echo "開始每日備份: $(date)"

# 備份 Grafana 資料
docker exec grafana tar czf - /var/lib/grafana > $BACKUP_DIR/grafana-data.tar.gz

# 備份配置檔案
tar czf $BACKUP_DIR/configs.tar.gz \
    grafana/ \
    mimir/ \
    tempo/ \
    loki/ \
    otel-collector-config.yaml \
    docker-compose.yml

# 備份資料庫
docker exec postgres pg_dumpall -U postgres | gzip > $BACKUP_DIR/postgres-all.sql.gz

# 清理 7 天前的備份
find /backup -type d -mtime +7 -exec rm -rf {} +

echo "每日備份完成: $(date)"
```

#### 設定自動備份

```bash
# 設定 crontab
crontab -e

# 每天凌晨 3 點執行備份
0 3 * * * /path/to/daily-backup.sh >> /var/log/backup.log 2>&1
```

### 災難恢復程序

#### 完整系統恢復

```bash
#!/bin/bash
# disaster-recovery.sh

BACKUP_DATE=$1
BACKUP_DIR="/backup/$BACKUP_DATE"

if [ ! -d "$BACKUP_DIR" ]; then
    echo "備份目錄不存在: $BACKUP_DIR"
    exit 1
fi

echo "開始災難恢復: $(date)"

# 停止所有服務
docker-compose down

# 恢復配置檔案
tar xzf $BACKUP_DIR/configs.tar.gz

# 恢復 Grafana 資料
docker-compose up -d grafana
sleep 30
docker exec grafana tar xzf - -C / < $BACKUP_DIR/grafana-data.tar.gz
docker-compose restart grafana

# 恢復資料庫
docker-compose up -d postgres
sleep 30
gunzip -c $BACKUP_DIR/postgres-all.sql.gz | docker exec -i postgres psql -U postgres

# 啟動所有服務
docker-compose up -d

echo "災難恢復完成: $(date)"
```

## 安全維護

### 定期安全檢查

#### 安全檢查清單

```bash
#!/bin/bash
# security-check.sh

echo "=== 安全檢查清單 ==="

# 檢查 Docker 映像更新
echo "1. 檢查 Docker 映像更新:"
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.CreatedAt}}"

# 檢查開放端口
echo "2. 檢查開放端口:"
netstat -tlnp | grep LISTEN

# 檢查 SSL 憑證到期時間
echo "3. 檢查 SSL 憑證:"
if [ -f "certs/ca-cert.pem" ]; then
    openssl x509 -in certs/ca-cert.pem -text -noout | grep "Not After"
fi

# 檢查 API 金鑰輪換
echo "4. 檢查 API 金鑰:"
find api-keys/ -name "*.txt" -mtime +90 -ls

echo "=== 安全檢查完成 ==="
```

### 憑證和金鑰管理

#### 自動憑證更新

```bash
#!/bin/bash
# cert-renewal.sh

# 檢查憑證是否即將到期（30 天內）
if openssl x509 -checkend 2592000 -noout -in certs/ca-cert.pem; then
    echo "憑證仍然有效"
else
    echo "憑證即將到期，開始更新..."
    
    # 備份舊憑證
    cp -r certs/ certs-backup-$(date +%Y%m%d)/
    
    # 生成新憑證
    ./otel-agent/generate-tls-certificates.sh
    
    # 重啟服務以載入新憑證
    docker-compose restart otel-collector
    
    echo "憑證更新完成"
fi
```

#### API 金鑰輪換

```bash
#!/bin/bash
# api-key-rotation.sh

echo "開始 API 金鑰輪換..."

# 備份舊金鑰
cp -r api-keys/ api-keys-backup-$(date +%Y%m%d)/

# 生成新金鑰
./otel-agent/api-key-management.sh rotate-all

# 重啟服務以載入新金鑰
docker-compose restart

echo "API 金鑰輪換完成"
```

## 故障排除

### 常見問題診斷

#### 服務連接問題

```bash
#!/bin/bash
# connectivity-check.sh

echo "=== 連接性診斷 ==="

# 檢查網路連接
services=(
    "mimir:9009"
    "tempo:3200"
    "loki:3100"
    "grafana:3000"
    "otel-collector:4317"
)

for service in "${services[@]}"; do
    host=$(echo $service | cut -d: -f1)
    port=$(echo $service | cut -d: -f2)
    
    if nc -z localhost $port; then
        echo "✓ $host:$port 連接正常"
    else
        echo "✗ $host:$port 連接失敗"
        
        # 檢查容器狀態
        docker-compose ps $host
        
        # 檢查容器日誌
        echo "最近的錯誤日誌:"
        docker-compose logs --tail=10 $host | grep -i error
    fi
done
```

#### 效能問題診斷

```bash
#!/bin/bash
# performance-diagnosis.sh

echo "=== 效能診斷 ==="

# 檢查系統資源
echo "1. 系統資源使用:"
echo "CPU: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}')"
echo "記憶體: $(free | grep Mem | awk '{printf "%.1f%%\n", $3/$2 * 100.0}')"
echo "磁碟: $(df -h / | tail -1 | awk '{print $5}')"

# 檢查 Docker 容器資源使用
echo "2. 容器資源使用:"
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}"

# 檢查網路延遲
echo "3. 網路延遲測試:"
ping -c 3 localhost

# 檢查磁碟 I/O
echo "4. 磁碟 I/O 統計:"
iostat -x 1 3
```

## 維護計劃

### 每日維護任務

- [ ] 檢查系統健康狀況
- [ ] 查看錯誤日誌
- [ ] 監控資源使用情況
- [ ] 檢查告警狀態

### 每週維護任務

- [ ] 執行效能基準測試
- [ ] 檢查備份完整性
- [ ] 更新安全補丁
- [ ] 清理舊日誌和數據

### 每月維護任務

- [ ] 執行完整安全檢查
- [ ] 檢查憑證到期時間
- [ ] 輪換 API 金鑰
- [ ] 執行災難恢復演練
- [ ] 檢查和更新文件

### 每季維護任務

- [ ] 升級 OpenTelemetry 版本
- [ ] 升級 LGTM 堆疊版本
- [ ] 檢查和優化配置
- [ ] 執行容量規劃
- [ ] 安全審計

## 聯絡資訊

如需技術支援，請聯絡：

- **系統管理員**: admin@company.com
- **開發團隊**: dev-team@company.com
- **緊急聯絡**: +886-xxx-xxx-xxx

## 相關文件

- [部署指南](DEPLOYMENT_GUIDE.md)
- [故障排除指南](TROUBLESHOOTING_GUIDE.md)
- [API 文件](API_DOCUMENTATION.md)