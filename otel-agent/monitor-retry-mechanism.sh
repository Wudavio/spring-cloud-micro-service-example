#!/bin/bash

# OpenTelemetry Collector 重試機制監控腳本
# 此腳本監控 Collector 的重試行為和錯誤處理

set -e

COLLECTOR_METRICS_URL="http://localhost:8888/metrics"
COLLECTOR_HEALTH_URL="http://localhost:13133/health"
LOG_FILE="/tmp/otel-collector-retry-monitor.log"
ERROR_LOG_FILE="/tmp/otel-collector-errors.log"

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 記錄函數
log() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARNING:${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')] SUCCESS:${NC} $1" | tee -a "$LOG_FILE"
}

# 檢查 Collector 健康狀況
check_collector_health() {
    log "檢查 OpenTelemetry Collector 健康狀況..."
    
    if curl -s -f "$COLLECTOR_HEALTH_URL" > /dev/null 2>&1; then
        log_success "Collector 健康檢查通過"
        return 0
    else
        log_error "Collector 健康檢查失敗"
        return 1
    fi
}

# 檢查重試指標
check_retry_metrics() {
    log "檢查重試相關指標..."
    
    if ! curl -s "$COLLECTOR_METRICS_URL" > /tmp/collector_metrics.txt 2>/dev/null; then
        log_error "無法獲取 Collector 指標"
        return 1
    fi
    
    # 檢查導出器指標
    local exporter_sent=$(grep -c "otelcol_exporter_sent_spans\|otelcol_exporter_sent_metric_points\|otelcol_exporter_sent_log_records" /tmp/collector_metrics.txt || echo "0")
    local exporter_failed=$(grep -c "otelcol_exporter_send_failed_spans\|otelcol_exporter_send_failed_metric_points\|otelcol_exporter_send_failed_log_records" /tmp/collector_metrics.txt || echo "0")
    
    log "導出成功指標數量: $exporter_sent"
    log "導出失敗指標數量: $exporter_failed"
    
    # 檢查重試指標
    local retry_metrics=$(grep -c "otelcol_exporter_retry" /tmp/collector_metrics.txt || echo "0")
    log "重試相關指標數量: $retry_metrics"
    
    # 檢查佇列指標
    local queue_metrics=$(grep -c "otelcol_exporter_queue" /tmp/collector_metrics.txt || echo "0")
    log "佇列相關指標數量: $queue_metrics"
    
    if [ "$exporter_failed" -gt 0 ]; then
        log_warning "檢測到導出失敗，檢查重試機制是否正常工作"
        
        # 顯示失敗指標詳情
        grep "otelcol_exporter_send_failed" /tmp/collector_metrics.txt | head -5 | while read line; do
            log_warning "失敗指標: $line"
        done
    fi
    
    rm -f /tmp/collector_metrics.txt
}

# 檢查錯誤日誌
check_error_logs() {
    log "檢查錯誤日誌..."
    
    if [ -f "$ERROR_LOG_FILE" ]; then
        local error_count=$(wc -l < "$ERROR_LOG_FILE" 2>/dev/null || echo "0")
        log "錯誤日誌行數: $error_count"
        
        if [ "$error_count" -gt 0 ]; then
            log_warning "發現錯誤日誌，顯示最近的錯誤:"
            tail -5 "$ERROR_LOG_FILE" | while read line; do
                log_error "$line"
            done
        else
            log_success "沒有發現錯誤日誌"
        fi
    else
        log "錯誤日誌檔案不存在"
    fi
}

# 測試重試機制
test_retry_mechanism() {
    log "測試重試機制..."
    
    # 模擬網路問題（暫時阻止對 Mimir 的連接）
    log "模擬網路連接問題..."
    
    # 檢查是否有 iptables（需要 root 權限）
    if command -v iptables >/dev/null 2>&1 && [ "$EUID" -eq 0 ]; then
        log "使用 iptables 模擬網路問題（需要 root 權限）"
        
        # 阻止對 Mimir 的連接
        iptables -A OUTPUT -p tcp --dport 9009 -j DROP
        
        log "等待 30 秒觀察重試行為..."
        sleep 30
        
        # 檢查重試指標
        check_retry_metrics
        
        # 恢復連接
        iptables -D OUTPUT -p tcp --dport 9009 -j DROP
        log "恢復網路連接"
        
        log "等待 30 秒觀察恢復情況..."
        sleep 30
        
        check_retry_metrics
    else
        log_warning "無法模擬網路問題（需要 root 權限和 iptables）"
        log "跳過網路模擬測試，僅檢查當前狀態"
    fi
}

# 檢查配置檔案
check_configuration() {
    log "檢查重試配置..."
    
    local config_file="../otel-collector-config.yaml"
    if [ -f "$config_file" ]; then
        log_success "找到配置檔案: $config_file"
        
        # 檢查重試配置
        if grep -q "retry_on_failure:" "$config_file"; then
            log_success "配置檔案包含重試配置"
            
            # 顯示重試配置詳情
            log "重試配置詳情:"
            grep -A 10 "retry_on_failure:" "$config_file" | while read line; do
                log "  $line"
            done
        else
            log_error "配置檔案缺少重試配置"
        fi
        
        # 檢查發送佇列配置
        if grep -q "sending_queue:" "$config_file"; then
            log_success "配置檔案包含發送佇列配置"
        else
            log_warning "配置檔案缺少發送佇列配置"
        fi
    else
        log_error "找不到配置檔案: $config_file"
    fi
}

# 生成報告
generate_report() {
    log "生成重試機制監控報告..."
    
    local report_file="/tmp/otel-retry-report-$(date +%Y%m%d-%H%M%S).txt"
    
    cat > "$report_file" << EOF
OpenTelemetry Collector 重試機制監控報告
生成時間: $(date)
========================================

配置檢查:
$(check_configuration 2>&1)

健康狀況檢查:
$(check_collector_health 2>&1)

指標檢查:
$(check_retry_metrics 2>&1)

錯誤日誌檢查:
$(check_error_logs 2>&1)

建議:
1. 定期檢查 Collector 健康狀況
2. 監控重試指標以識別網路問題
3. 檢查錯誤日誌以發現配置問題
4. 調整重試參數以適應網路環境

EOF

    log_success "報告已生成: $report_file"
}

# 主函數
main() {
    log "開始 OpenTelemetry Collector 重試機制監控..."
    
    # 創建日誌目錄
    mkdir -p "$(dirname "$LOG_FILE")"
    
    # 檢查配置
    check_configuration
    
    # 檢查健康狀況
    check_collector_health
    
    # 檢查指標
    check_retry_metrics
    
    # 檢查錯誤日誌
    check_error_logs
    
    # 測試重試機制（可選）
    if [ "${1:-}" = "--test-retry" ]; then
        test_retry_mechanism
    fi
    
    # 生成報告
    generate_report
    
    log_success "監控完成"
}

# 執行主函數
main "$@"