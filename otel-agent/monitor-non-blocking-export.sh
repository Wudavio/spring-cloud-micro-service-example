#!/bin/bash

# OpenTelemetry 非阻塞導出監控腳本
# 監控 Collector 和微服務的導出效能

set -e

echo "=== OpenTelemetry 非阻塞導出監控 ==="
echo "開始時間: $(date)"
echo

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 檢查 Collector 健康狀態
check_collector_health() {
    echo -e "${BLUE}檢查 OpenTelemetry Collector 健康狀態...${NC}"
    
    if curl -s -f http://localhost:13133/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Collector 健康檢查通過${NC}"
        
        # 獲取 Collector 指標
        echo -e "${BLUE}獲取 Collector 效能指標...${NC}"
        curl -s http://localhost:8889/metrics | grep -E "(otelcol_processor_batch_|otelcol_exporter_|otelcol_receiver_)" | head -20
    else
        echo -e "${RED}✗ Collector 健康檢查失敗${NC}"
        return 1
    fi
}

# 檢查佇列狀態
check_queue_status() {
    echo -e "${BLUE}檢查導出佇列狀態...${NC}"
    
    # 檢查 Collector 內部狀態
    if curl -s http://localhost:55679 > /dev/null 2>&1; then
        echo -e "${GREEN}✓ zPages 可用，檢查內部狀態${NC}"
        echo "訪問 http://localhost:55679 查看詳細的內部狀態"
    else
        echo -e "${YELLOW}⚠ zPages 不可用${NC}"
    fi
    
    # 檢查備份檔案大小
    if [ -f "/tmp/otel-backup.json" ]; then
        backup_size=$(du -h /tmp/otel-backup.json 2>/dev/null | cut -f1 || echo "0")
        echo -e "${BLUE}備份檔案大小: ${backup_size}${NC}"
    fi
}

# 檢查微服務連接狀態
check_microservices_connection() {
    echo -e "${BLUE}檢查微服務 OpenTelemetry 連接狀態...${NC}"
    
    services=("eureka-server:8761" "config-server:8888" "api-gateway:8080" "product-service:8081" "inventory-service:8082" "order-service:8083" "auth-service:8084")
    
    for service in "${services[@]}"; do
        service_name=$(echo $service | cut -d: -f1)
        service_port=$(echo $service | cut -d: -f2)
        
        if curl -s -f http://localhost:${service_port}/actuator/health > /dev/null 2>&1; then
            echo -e "${GREEN}✓ ${service_name} 健康且可連接${NC}"
        else
            echo -e "${RED}✗ ${service_name} 不可用${NC}"
        fi
    done
}

# 檢查 LGTM 堆疊狀態
check_lgtm_stack() {
    echo -e "${BLUE}檢查 LGTM 堆疊狀態...${NC}"
    
    # 檢查 Mimir
    if curl -s -f http://localhost:9009/ready > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Mimir 可用${NC}"
    else
        echo -e "${RED}✗ Mimir 不可用${NC}"
    fi
    
    # 檢查 Tempo
    if curl -s -f http://localhost:3200/ready > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Tempo 可用${NC}"
    else
        echo -e "${RED}✗ Tempo 不可用${NC}"
    fi
    
    # 檢查 Loki
    if curl -s -f http://localhost:3100/ready > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Loki 可用${NC}"
    else
        echo -e "${RED}✗ Loki 不可用${NC}"
    fi
    
    # 檢查 Grafana
    if curl -s -f http://localhost:3000/api/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Grafana 可用${NC}"
    else
        echo -e "${RED}✗ Grafana 不可用${NC}"
    fi
}

# 測試非阻塞導出效能
test_non_blocking_performance() {
    echo -e "${BLUE}測試非阻塞導出效能...${NC}"
    
    # 發送測試請求到各個服務
    echo "發送測試請求以生成遙測數據..."
    
    # 測試 API Gateway
    for i in {1..10}; do
        curl -s http://localhost:8080/actuator/health > /dev/null &
    done
    
    # 測試 Product Service
    for i in {1..10}; do
        curl -s http://localhost:8081/actuator/health > /dev/null &
    done
    
    wait
    
    echo "等待 5 秒讓數據處理..."
    sleep 5
    
    # 檢查 Collector 指標
    echo -e "${BLUE}檢查處理後的 Collector 指標...${NC}"
    curl -s http://localhost:8889/metrics | grep -E "(otelcol_processor_batch_batch_send_size|otelcol_exporter_sent)" | head -10
}

# 主函數
main() {
    echo "開始監控非阻塞導出機制..."
    echo
    
    check_collector_health
    echo
    
    check_queue_status
    echo
    
    check_microservices_connection
    echo
    
    check_lgtm_stack
    echo
    
    test_non_blocking_performance
    echo
    
    echo -e "${GREEN}監控完成！${NC}"
    echo "結束時間: $(date)"
}

# 執行主函數
main "$@"