#!/bin/bash

# OpenTelemetry 重試機制測試腳本
# 此腳本測試各種故障情況下的重試行為

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
COLLECTOR_URL="http://localhost:4317"
MIMIR_URL="http://localhost:9009"
TEMPO_URL="http://localhost:3200"
LOKI_URL="http://localhost:3100"
TEST_DURATION=60
LOG_FILE="/tmp/retry-test-$(date +%Y%m%d-%H%M%S).log"

# 記錄函數
log() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')] SUCCESS:${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARNING:${NC} $1" | tee -a "$LOG_FILE"
}

# 檢查服務可用性
check_service() {
    local service_name=$1
    local service_url=$2
    
    log "檢查 $service_name 服務可用性..."
    
    if curl -s -f "$service_url" > /dev/null 2>&1; then
        log_success "$service_name 服務可用"
        return 0
    else
        log_warning "$service_name 服務不可用"
        return 1
    fi
}

# 生成測試遙測數據
generate_test_data() {
    log "生成測試遙測數據..."
    
    # 使用 curl 發送 OTLP 數據到 Collector
    local test_data='{
        "resourceSpans": [{
            "resource": {
                "attributes": [{
                    "key": "service.name",
                    "value": {"stringValue": "retry-test-service"}
                }]
            },
            "instrumentationLibrarySpans": [{
                "spans": [{
                    "traceId": "5B8EFFF798038103D269B633813FC60C",
                    "spanId": "EEE19B7EC3C1B174",
                    "name": "test-span",
                    "startTimeUnixNano": "'$(date +%s%N)'",
                    "endTimeUnixNano": "'$(date +%s%N)'",
                    "attributes": [{
                        "key": "test.retry",
                        "value": {"stringValue": "true"}
                    }]
                }]
            }]
        }]
    }'
    
    # 發送測試數據
    if curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$test_data" \
        "$COLLECTOR_URL/v1/traces" > /dev/null 2>&1; then
        log_success "測試追蹤數據發送成功"
    else
        log_error "測試追蹤數據發送失敗"
    fi
    
    # 生成測試指標數據
    local metric_data='{
        "resourceMetrics": [{
            "resource": {
                "attributes": [{
                    "key": "service.name",
                    "value": {"stringValue": "retry-test-service"}
                }]
            },
            "instrumentationLibraryMetrics": [{
                "metrics": [{
                    "name": "test_counter",
                    "description": "Test counter for retry mechanism",
                    "unit": "1",
                    "sum": {
                        "dataPoints": [{
                            "attributes": [{
                                "key": "test.retry",
                                "value": {"stringValue": "true"}
                            }],
                            "timeUnixNano": "'$(date +%s%N)'",
                            "asInt": "1"
                        }],
                        "aggregationTemporality": 2,
                        "isMonotonic": true
                    }
                }]
            }]
        }]
    }'
    
    if curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$metric_data" \
        "$COLLECTOR_URL/v1/metrics" > /dev/null 2>&1; then
        log_success "測試指標數據發送成功"
    else
        log_error "測試指標數據發送失敗"
    fi
}

# 模擬網路故障
simulate_network_failure() {
    local target_port=$1
    local service_name=$2
    
    log "模擬 $service_name 網路故障（端口 $target_port）..."
    
    if [ "$EUID" -ne 0 ]; then
        log_warning "需要 root 權限來模擬網路故障，跳過此測試"
        return 1
    fi
    
    # 阻止對目標端口的連接
    iptables -A OUTPUT -p tcp --dport "$target_port" -j DROP
    log "已阻止對端口 $target_port 的連接"
    
    return 0
}

# 恢復網路連接
restore_network() {
    local target_port=$1
    local service_name=$2
    
    if [ "$EUID" -ne 0 ]; then
        return 1
    fi
    
    log "恢復 $service_name 網路連接（端口 $target_port）..."
    
    # 移除阻止規則
    iptables -D OUTPUT -p tcp --dport "$target_port" -j DROP 2>/dev/null || true
    log "已恢復對端口 $target_port 的連接"
}

# 監控重試指標
monitor_retry_metrics() {
    local duration=$1
    log "監控重試指標 $duration 秒..."
    
    local start_time=$(date +%s)
    local end_time=$((start_time + duration))
    
    while [ $(date +%s) -lt $end_time ]; do
        # 獲取 Collector 指標
        if curl -s "http://localhost:8888/metrics" > /tmp/metrics_snapshot.txt 2>/dev/null; then
            # 檢查重試相關指標
            local retry_count=$(grep -c "otelcol_exporter_retry" /tmp/metrics_snapshot.txt 2>/dev/null || echo "0")
            local failed_count=$(grep -c "send_failed" /tmp/metrics_snapshot.txt 2>/dev/null || echo "0")
            local queue_size=$(grep "otelcol_exporter_queue_size" /tmp/metrics_snapshot.txt | tail -1 | awk '{print $2}' 2>/dev/null || echo "0")
            
            log "重試指標: $retry_count, 失敗指標: $failed_count, 佇列大小: $queue_size"
        else
            log_warning "無法獲取 Collector 指標"
        fi
        
        sleep 5
    done
    
    rm -f /tmp/metrics_snapshot.txt
}

# 測試指標導出重試
test_metrics_retry() {
    log "測試指標導出重試機制..."
    
    # 檢查 Mimir 初始狀態
    check_service "Mimir" "$MIMIR_URL/ready"
    
    # 模擬 Mimir 故障
    if simulate_network_failure 9009 "Mimir"; then
        log "開始生成測試數據並監控重試行為..."
        
        # 在背景中持續生成測試數據
        (
            for i in {1..20}; do
                generate_test_data
                sleep 3
            done
        ) &
        local data_gen_pid=$!
        
        # 監控重試指標
        monitor_retry_metrics 30
        
        # 恢復 Mimir 連接
        restore_network 9009 "Mimir"
        
        # 等待數據生成完成
        wait $data_gen_pid
        
        # 再監控一段時間觀察恢復情況
        log "觀察服務恢復後的行為..."
        monitor_retry_metrics 30
    else
        log_warning "跳過網路故障模擬測試"
    fi
}

# 測試追蹤導出重試
test_traces_retry() {
    log "測試追蹤導出重試機制..."
    
    # 檢查 Tempo 初始狀態
    check_service "Tempo" "$TEMPO_URL/ready"
    
    # 模擬 Tempo 故障
    if simulate_network_failure 3200 "Tempo"; then
        log "開始生成測試追蹤數據..."
        
        # 生成測試追蹤數據
        for i in {1..10}; do
            generate_test_data
            sleep 2
        done
        
        # 監控重試指標
        monitor_retry_metrics 20
        
        # 恢復 Tempo 連接
        restore_network 3200 "Tempo"
        
        # 觀察恢復情況
        monitor_retry_metrics 20
    else
        log_warning "跳過 Tempo 故障模擬測試"
    fi
}

# 測試佇列行為
test_queue_behavior() {
    log "測試發送佇列行為..."
    
    # 同時模擬多個服務故障
    if [ "$EUID" -eq 0 ]; then
        simulate_network_failure 9009 "Mimir"
        simulate_network_failure 3200 "Tempo"
        simulate_network_failure 3100 "Loki"
        
        log "所有後端服務已斷開，測試佇列行為..."
        
        # 大量生成數據測試佇列
        (
            for i in {1..50}; do
                generate_test_data
                sleep 1
            done
        ) &
        local data_gen_pid=$!
        
        # 監控佇列指標
        monitor_retry_metrics 60
        
        # 逐步恢復服務
        log "逐步恢復服務..."
        restore_network 9009 "Mimir"
        sleep 10
        
        restore_network 3200 "Tempo"
        sleep 10
        
        restore_network 3100 "Loki"
        
        # 等待數據生成完成
        wait $data_gen_pid
        
        # 觀察恢復情況
        monitor_retry_metrics 30
    else
        log_warning "需要 root 權限來測試佇列行為"
    fi
}

# 生成測試報告
generate_test_report() {
    log "生成重試機制測試報告..."
    
    local report_file="/tmp/retry-test-report-$(date +%Y%m%d-%H%M%S).txt"
    
    cat > "$report_file" << EOF
OpenTelemetry 重試機制測試報告
測試時間: $(date)
========================================

測試概要:
- 測試了指標導出重試機制
- 測試了追蹤導出重試機制  
- 測試了發送佇列行為
- 監控了重試相關指標

測試結果:
$(tail -50 "$LOG_FILE")

建議:
1. 檢查重試配置是否合理
2. 監控佇列大小避免記憶體溢出
3. 調整重試間隔以適應網路環境
4. 設置適當的告警閾值

配置建議:
- initial_interval: 1s
- max_interval: 60s  
- max_elapsed_time: 600s
- max_retry_attempts: 10
- queue_size: 5000

EOF

    log_success "測試報告已生成: $report_file"
}

# 清理函數
cleanup() {
    log "清理測試環境..."
    
    if [ "$EUID" -eq 0 ]; then
        # 清理所有可能的 iptables 規則
        iptables -D OUTPUT -p tcp --dport 9009 -j DROP 2>/dev/null || true
        iptables -D OUTPUT -p tcp --dport 3200 -j DROP 2>/dev/null || true
        iptables -D OUTPUT -p tcp --dport 3100 -j DROP 2>/dev/null || true
    fi
    
    # 清理臨時檔案
    rm -f /tmp/metrics_snapshot.txt
    
    log "清理完成"
}

# 設置清理陷阱
trap cleanup EXIT

# 主函數
main() {
    log "開始 OpenTelemetry 重試機制測試..."
    
    # 檢查初始服務狀態
    log "檢查服務初始狀態..."
    check_service "OpenTelemetry Collector" "$COLLECTOR_URL"
    check_service "Mimir" "$MIMIR_URL/ready"
    check_service "Tempo" "$TEMPO_URL/ready"  
    check_service "Loki" "$LOKI_URL/ready"
    
    # 執行測試
    case "${1:-all}" in
        "metrics")
            test_metrics_retry
            ;;
        "traces")
            test_traces_retry
            ;;
        "queue")
            test_queue_behavior
            ;;
        "all")
            test_metrics_retry
            test_traces_retry
            test_queue_behavior
            ;;
        *)
            log_error "未知的測試類型: $1"
            log "使用方法: $0 [metrics|traces|queue|all]"
            exit 1
            ;;
    esac
    
    # 生成報告
    generate_test_report
    
    log_success "重試機制測試完成"
}

# 執行主函數
main "$@"