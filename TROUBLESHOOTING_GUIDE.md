# OpenTelemetry LGTM 整合故障排除指南

## 概覽

本指南提供了 OpenTelemetry LGTM 可觀測性堆疊常見問題的診斷和解決方案。

## 快速診斷工具

### 系統狀態檢查腳本

```bash
#!/bin/bash
# quick-diagnosis.sh

echo "=== OpenTelemetry LGTM 快速診斷 ==="
echo "診斷時間: $(date)"
echo

# 1. 檢查 Docker 服務狀態
echo "1. Docker 服務狀態:"
docker-compose ps

echo
echo "2. 檢查容器健康狀況:"
docker-compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"

echo
echo "3. 檢查系統資源:"
echo "CPU 使用率: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}')"
echo "記憶體使用率: $(free | grep Mem | awk '{printf "%.1f%%\n", $3/$2 * 100.0}')"
echo "磁碟使用率: $(df -h / | tail -1 | awk '{print $5}')"

echo
echo "4. 檢查網路連接:"
services=("localhost:3000" "localhost:9009" "localhost:3200" "localhost:3100" "localhost:4317")
for service in "${services[@]}"; do
    if nc -z ${service/:/ }; then
        echo "✓ $service 連接正常"
    else
        echo "✗ $service 連接失敗"
    fi
done

echo
echo "=== 快速診斷完成 ==="
```

## 常見問題和解決方案

### 1. 服務啟動問題

#### 問題：容器無法啟動

**症狀：**
```
ERROR: for eureka-server  Cannot start service eureka-server: driver failed programming external connectivity
```

**診斷步驟：**
```bash
# 檢查端口占用
netstat -tlnp | grep :8761

# 檢查 Docker 網路
docker network ls
docker network inspect spring-cloud-micro-service-example_default
```

**解決方案：**
```bash
# 方案 1: 停止占用端口的程序
sudo lsof -ti:8761 | xargs kill -9

# 方案 2: 修改端口配置
# 編輯 docker-compose.yml，更改端口映射
ports:
  - "8762:8761"  # 使用不同的外部端口

# 方案 3: 重置 Docker 網路
docker-compose down
docker network prune -f
docker-compose up -d
```

#### 問題：服務依賴啟動順序錯誤

**症狀：**
```
eureka-server    | Connection refused: connect to config-server:8888
```

**解決方案：**
```bash
# 按正確順序啟動服務
docker-compose up -d postgres redis
sleep 10

docker-compose up -d mimir tempo loki grafana otel-collector
sleep 20

docker-compose up -d eureka-server
sleep 30

docker-compose up -d config-server
sleep 20

docker-compose up -d auth-service product-service inventory-service
sleep 30

docker-compose up -d order-service api-gateway
```

### 2. OpenTelemetry 數據收集問題

#### 問題：遙測數據未出現在 Grafana

**診斷步驟：**
```bash
# 1. 檢查 OpenTelemetry Collector 狀態
curl http://localhost:13133/health

# 2. 檢查 Collector 日誌
docker-compose logs otel-collector | tail -50

# 3. 檢查服務是否正確配置 OpenTelemetry
docker-compose logs eureka-server | grep -i otel

# 4. 檢查 LGTM 服務狀態
curl http://localhost:9009/ready  # Mimir
curl http://localhost:3200/ready  # Tempo
curl http://localhost:3100/ready  # Loki
```

**常見原因和解決方案：**

1. **OpenTelemetry Agent 未載入**
```bash
# 檢查 JVM 參數
docker-compose exec eureka-server ps aux | grep java

# 確保包含 -javaagent 參數
# 如果沒有，檢查 Dockerfile 中的 ENTRYPOINT
```

2. **Collector 配置錯誤**
```bash
# 驗證 Collector 配置
docker-compose exec otel-collector /otelcol --config=/etc/otel-collector-config.yaml --dry-run

# 檢查配置檔案語法
yamllint otel-collector-config.yaml
```

3. **網路連接問題**
```bash
# 測試服務間連接
docker-compose exec eureka-server nc -zv otel-collector 4317
docker-compose exec otel-collector nc -zv mimir 9009
```

#### 問題：指標數據不完整

**診斷步驟：**
```bash
# 檢查 Prometheus 指標端點
curl http://localhost:8761/actuator/prometheus

# 檢查 Mimir 是否接收到數據
curl http://localhost:9009/api/v1/query?query=up

# 檢查 Collector 指標處理統計
curl http://localhost:8889/metrics | grep otelcol_processor
```

**解決方案：**
```yaml
# 在 application.yml 中確保 Actuator 端點已啟用
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### 3. 效能問題

#### 問題：系統響應緩慢

**診斷步驟：**
```bash
# 1. 檢查系統資源使用
top -p $(pgrep -d',' java)
docker stats --no-stream

# 2. 檢查 JVM 記憶體使用
docker-compose exec eureka-server jstat -gc 1

# 3. 檢查網路延遲
ping -c 10 localhost
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8080/actuator/health

# 4. 檢查資料庫連接
docker-compose exec postgres psql -U postgres -c "SELECT count(*) FROM pg_stat_activity;"
```

**解決方案：**

1. **調整 JVM 記憶體設定**
```yaml
# 在 docker-compose.yml 中增加記憶體
environment:
  - JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC
```

2. **優化 OpenTelemetry 配置**
```yaml
# 降低取樣率
- OTEL_TRACES_SAMPLER_ARG=0.01

# 增加批次大小
- OTEL_BSP_MAX_EXPORT_BATCH_SIZE=2048
- OTEL_BSP_SCHEDULE_DELAY=1000
```

3. **調整資料庫連接池**
```yaml
# 在 application.yml 中調整
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

#### 問題：記憶體洩漏

**診斷步驟：**
```bash
# 1. 監控記憶體使用趨勢
docker stats --format "table {{.Container}}\t{{.MemUsage}}\t{{.MemPerc}}" --no-stream

# 2. 生成 JVM 堆轉儲
docker-compose exec eureka-server jcmd 1 GC.run_finalization
docker-compose exec eureka-server jcmd 1 VM.gc
docker-compose exec eureka-server jcmd 1 GC.heap_dump /tmp/heapdump.hprof

# 3. 檢查 GC 統計
docker-compose exec eureka-server jstat -gc 1 5s 10
```

**解決方案：**
```yaml
# 啟用 GC 日誌記錄
environment:
  - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:/tmp/gc.log
```

### 4. 網路和連接問題

#### 問題：服務間通信失敗

**症狀：**
```
Connection refused: connect to product-service:8081
```

**診斷步驟：**
```bash
# 1. 檢查服務發現
curl http://localhost:8761/eureka/apps

# 2. 檢查 DNS 解析
docker-compose exec api-gateway nslookup product-service

# 3. 檢查網路連接
docker-compose exec api-gateway nc -zv product-service 8081

# 4. 檢查防火牆規則
iptables -L
```

**解決方案：**
```bash
# 1. 重啟網路相關服務
docker-compose restart eureka-server
sleep 30
docker-compose restart api-gateway

# 2. 檢查服務註冊配置
# 確保 eureka.client.service-url.defaultZone 正確設定

# 3. 重建 Docker 網路
docker-compose down
docker network prune -f
docker-compose up -d
```

#### 問題：LGTM 服務無法訪問

**診斷步驟：**
```bash
# 檢查各個服務的健康狀況
curl -v http://localhost:3000/api/health    # Grafana
curl -v http://localhost:9009/ready         # Mimir
curl -v http://localhost:3200/ready         # Tempo
curl -v http://localhost:3100/ready         # Loki

# 檢查容器日誌
docker-compose logs grafana | tail -20
docker-compose logs mimir | tail -20
docker-compose logs tempo | tail -20
docker-compose logs loki | tail -20
```

**解決方案：**
```bash
# 1. 重啟 LGTM 服務
docker-compose restart grafana mimir tempo loki

# 2. 檢查配置檔案
# 驗證 grafana/grafana.ini
# 驗證 mimir/mimir.yaml
# 驗證 tempo/tempo.yaml
# 驗證 loki/loki.yaml

# 3. 檢查資料卷權限
docker-compose exec grafana ls -la /var/lib/grafana
docker-compose exec mimir ls -la /data/mimir
```

### 5. 資料問題

#### 問題：Grafana 儀表板顯示無數據

**診斷步驟：**
```bash
# 1. 檢查資料源連接
# 在 Grafana UI 中測試資料源連接

# 2. 檢查查詢語法
# 在 Grafana Explore 中測試查詢

# 3. 檢查時間範圍
# 確保查詢的時間範圍內有數據

# 4. 檢查標籤和指標名稱
curl http://localhost:9009/api/v1/label/__name__/values
```

**解決方案：**
```bash
# 1. 重新配置資料源
# 刪除並重新添加 Grafana 資料源

# 2. 檢查指標標籤
curl http://localhost:8761/actuator/prometheus | grep jvm_memory

# 3. 驗證 Collector 配置
# 確保指標正確路由到 Mimir
```

#### 問題：追蹤數據缺失

**診斷步驟：**
```bash
# 1. 檢查取樣配置
docker-compose logs eureka-server | grep -i sampl

# 2. 檢查 Tempo 接收統計
curl http://localhost:3200/metrics | grep tempo_distributor

# 3. 測試追蹤生成
curl -H "traceparent: 00-$(openssl rand -hex 16)-$(openssl rand -hex 8)-01" \
     http://localhost:8080/actuator/health
```

**解決方案：**
```yaml
# 調整取樣率
environment:
  - OTEL_TRACES_SAMPLER=always_on  # 臨時設定為 100% 取樣

# 檢查 Collector 追蹤管道配置
service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [otlp/tempo]
```

### 6. 安全問題

#### 問題：TLS 憑證錯誤

**症狀：**
```
certificate verify failed: self signed certificate
```

**診斷步驟：**
```bash
# 檢查憑證有效性
openssl x509 -in certs/ca-cert.pem -text -noout

# 檢查憑證到期時間
openssl x509 -in certs/ca-cert.pem -checkend 86400

# 測試 TLS 連接
openssl s_client -connect localhost:4327 -CAfile certs/ca-cert.pem
```

**解決方案：**
```bash
# 1. 重新生成憑證
./otel-agent/generate-tls-certificates.sh

# 2. 更新憑證配置
docker-compose restart otel-collector

# 3. 臨時禁用 TLS（僅用於測試）
# 在 docker-compose.yml 中設定
environment:
  - TLS_ENABLED=false
  - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
```

#### 問題：API 金鑰驗證失敗

**診斷步驟：**
```bash
# 檢查 API 金鑰檔案
ls -la api-keys/
cat api-keys/otel-collector-api-key.txt

# 檢查環境變數
docker-compose exec eureka-server env | grep OTEL

# 測試 API 金鑰
curl -H "Authorization: Bearer $(cat api-keys/mimir-api-key.txt)" \
     http://localhost:9009/api/v1/query?query=up
```

**解決方案：**
```bash
# 1. 重新生成 API 金鑰
./otel-agent/api-key-management.sh generate-all

# 2. 重啟服務載入新金鑰
docker-compose restart

# 3. 臨時禁用 API 金鑰驗證（僅用於測試）
# 移除 docker-compose.yml 中的 API 金鑰相關環境變數
```

## 日誌分析

### 重要日誌位置

```bash
# Docker Compose 日誌
docker-compose logs [service-name]

# 系統日誌
/var/log/syslog
/var/log/docker.log

# 應用程式日誌
docker-compose exec eureka-server ls /logs/

# OpenTelemetry Collector 日誌
docker-compose logs otel-collector
```

### 常見錯誤模式

#### 1. 記憶體不足錯誤
```
OutOfMemoryError: Java heap space
```
**解決方案：** 增加 JVM 堆記憶體大小

#### 2. 連接超時錯誤
```
SocketTimeoutException: Read timed out
```
**解決方案：** 調整連接和讀取超時設定

#### 3. 服務發現錯誤
```
No instances available for product-service
```
**解決方案：** 檢查 Eureka 服務註冊狀態

#### 4. 資料庫連接錯誤
```
Connection is not available, request timed out after 30000ms
```
**解決方案：** 調整資料庫連接池配置

## 效能調優檢查清單

### 系統層面
- [ ] CPU 使用率 < 80%
- [ ] 記憶體使用率 < 85%
- [ ] 磁碟 I/O 等待時間 < 10%
- [ ] 網路延遲 < 50ms

### 應用層面
- [ ] JVM 堆記憶體使用 < 80%
- [ ] GC 暫停時間 < 100ms
- [ ] 資料庫連接池使用率 < 80%
- [ ] HTTP 請求響應時間 < 200ms

### OpenTelemetry 層面
- [ ] 取樣率設定合理
- [ ] 批次處理配置優化
- [ ] 導出重試次數 < 5%
- [ ] Collector 記憶體使用 < 512MB

## 緊急處理程序

### 系統完全故障

1. **立即行動**
```bash
# 停止所有服務
docker-compose down

# 檢查系統資源
df -h
free -h
top

# 清理 Docker 資源
docker system prune -f
```

2. **診斷問題**
```bash
# 檢查系統日誌
journalctl -xe

# 檢查 Docker 日誌
docker-compose logs --tail=100
```

3. **恢復服務**
```bash
# 從最小配置開始
docker-compose up -d postgres redis
sleep 10

# 逐步啟動核心服務
docker-compose up -d eureka-server config-server
sleep 30

# 啟動其他服務
docker-compose up -d
```

### 資料遺失

1. **停止寫入操作**
```bash
# 停止所有微服務
docker-compose stop eureka-server config-server api-gateway product-service inventory-service order-service auth-service
```

2. **評估損失**
```bash
# 檢查資料庫狀態
docker-compose exec postgres psql -U postgres -c "\l"

# 檢查 LGTM 資料
docker-compose exec grafana ls -la /var/lib/grafana
```

3. **從備份恢復**
```bash
# 執行災難恢復程序
./disaster-recovery.sh 20240101
```

## 聯絡支援

### 收集診斷資訊

在聯絡技術支援前，請收集以下資訊：

```bash
#!/bin/bash
# collect-diagnostic-info.sh

DIAG_DIR="diagnostic-$(date +%Y%m%d-%H%M%S)"
mkdir -p $DIAG_DIR

# 系統資訊
uname -a > $DIAG_DIR/system-info.txt
docker --version >> $DIAG_DIR/system-info.txt
docker-compose --version >> $DIAG_DIR/system-info.txt

# 服務狀態
docker-compose ps > $DIAG_DIR/service-status.txt

# 系統資源
top -bn1 > $DIAG_DIR/system-resources.txt
df -h > $DIAG_DIR/disk-usage.txt
free -h > $DIAG_DIR/memory-usage.txt

# 服務日誌
docker-compose logs --tail=500 > $DIAG_DIR/all-services.log

# 配置檔案
cp docker-compose.yml $DIAG_DIR/
cp otel-collector-config.yaml $DIAG_DIR/

# 打包診斷資訊
tar czf $DIAG_DIR.tar.gz $DIAG_DIR/
echo "診斷資訊已收集到: $DIAG_DIR.tar.gz"
```

### 支援聯絡方式

- **緊急支援**: +886-xxx-xxx-xxx
- **技術支援**: support@company.com
- **開發團隊**: dev-team@company.com

提供診斷資訊時，請包含：
- 問題描述和重現步驟
- 錯誤訊息和日誌
- 系統環境資訊
- 診斷資訊包 (.tar.gz)

## 相關文件

- [部署指南](DEPLOYMENT_GUIDE.md)
- [操作指南](OPERATIONS_GUIDE.md)
- [API 文件](API_DOCUMENTATION.md)