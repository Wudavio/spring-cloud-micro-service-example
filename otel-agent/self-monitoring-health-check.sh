#!/bin/bash

# OpenTelemetry Collector 自監控健康檢查腳本
# 檢查 Collector 的健康狀況和效能指標

set -e

echo "=== OpenTelemetry Collector 自監控健康檢查 ==="
echo "開始時間: $(date)"
echo

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
COLLECTOR_HOST="localhost"
HEALTH_CHECK_PORT="13133"
METRICS_PORT="8889"
PPROF_PORT="1777"
ZPAGES_PORT="55679"

# 檢查健康狀態
check_health_status() {
    echo -e "${BLUE}檢查 Collector 健康狀態...${NC}"
    
    local health_response
    if health_response=$(curl -s -f "http://${COLLECTOR_HOST}:${HEALTH_CHECK_PORT}/health" 2>/dev/null); then
        echo -e "${GREEN}✓ Collector 健康檢查通過${NC}"
        echo "健康狀態響應: $health_response"
        return 0
    else
        echo -e "${RED}✗ Collector 健康檢查失敗${NC}"
        return 1
    fi
}

# 檢查自監控指標
check_self_monitoring_metrics() {
    echo -e "${BLUE}檢查 Collector 自監控指標...${NC}"
    
    local metrics_response
    if metrics_response=$(curl -s "http://${COLLECTOR_HOST}:${METRICS_PORT}/metrics" 2>/dev/null); then
        echo -e "${GREEN}✓ 自監控指標可用${NC}"
        
        # 檢查關鍵指標
        echo -e "${BLUE}分析關鍵效能指標...${NC}"
        
        # 檢查接收器指標
        local receiver_metrics
        receiver_metrics=$(echo "$metrics_response" | grep -E "otelcol_receiver_" | head -5)
        if [ -n "$receiver_metrics" ]; then
            echo -e "${GREEN}✓ 接收器指標正常${NC}"
            echo "$receiver_metrics"
        else
            echo -e "${YELLOW}⚠ 接收器指標不可用${NC}"
        fi
        
        # 檢查處理器指標
        local processor_metrics
        processor_metrics=$(echo "$metrics_response" | grep -E "otelcol_processor_" | head -5)
        if [ -n "$processor_metrics" ]; then
            echo -e "${GREEN}✓ 處理器指標正常${NC}"
            echo "$processor_metrics"
        else
            echo -e "${YELLOW}⚠ 處理器指標不可用${NC}"
        fi
        
        # 檢查導出器指標
        local exporter_metrics
        exporter_metrics=$(echo "$metrics_response" | grep -E "otelcol_exporter_" | head -5)
        if [ -n "$exporter_metrics" ]; then
            echo -e "${GREEN}✓ 導出器指標正常${NC}"
            echo "$exporter_metrics"
        else
            echo -e "${YELLOW}⚠ 導出器指標不可用${NC}"
        fi
        
        return 0
    else
        echo -e "${RED}✗ 自監控指標不可用${NC}"
        return 1
    fi
}

# 檢查記憶體使用情況
check_memory_usage() {
    echo -e "${BLUE}檢查記憶體使用情況...${NC}"
    
    local metrics_response
    if metrics_response=$(curl -s "http://${COLLECTOR_HOST}:${METRICS_PORT}/metrics" 2>/dev/null); then
        # 檢查記憶體相關指標
        local memory_metrics
        memory_metrics=$(echo "$metrics_response" | grep -E "(process_resident_memory_bytes|go_memstats_)" | head -10)
        
        if [ -n "$memory_metrics" ]; then
            echo -e "${GREEN}✓ 記憶體指標可用${NC}"
            echo "$memory_metrics"
            
            # 檢查記憶體使用是否超過閾值
            local memory_bytes
            memory_bytes=$(echo "$metrics_response" | grep "process_resident_memory_bytes" | awk '{print $2}' | head -1)
            
            if [ -n "$memory_bytes" ]; then
                local memory_mb=$((memory_bytes / 1024 / 1024))
                echo "當前記憶體使用: ${memory_mb} MB"
                
                if [ "$memory_mb" -gt 512 ]; then
                    echo -e "${YELLOW}⚠ 記憶體使用量較高: ${memory_mb} MB${NC}"
                elif [ "$memory_mb" -gt 1024 ]; then
                    echo -e "${RED}✗ 記憶體使用量過高: ${memory_mb} MB${NC}"
                    return 1
                else
                    echo -e "${GREEN}✓ 記憶體使用量正常: ${memory_mb} MB${NC}"
                fi
            fi
        else
            echo -e "${YELLOW}⚠ 記憶體指標不可用${NC}"
        fi
    else
        echo -e "${RED}✗ 無法獲取記憶體指標${NC}"
        return 1
    fi
}

# 檢查 CPU 使用情況
check_cpu_usage() {
    echo -e "${BLUE}檢查 CPU 使用情況...${NC}"
    
    local metrics_response
    if metrics_response=$(curl -s "http://${COLLECTOR_HOST}:${METRICS_PORT}/metrics" 2>/dev/null); then
        # 檢查 CPU 相關指標
        local cpu_metrics
        cpu_metrics=$(echo "$metrics_response" | grep -E "(process_cpu_seconds_total|go_gc_duration_seconds)" | head -5)
        
        if [ -n "$cpu_metrics" ]; then
            echo -e "${GREEN}✓ CPU 指標可用${NC}"
            echo "$cpu_metrics"
        else
            echo -e "${YELLOW}⚠ CPU 指標不可用${NC}"
        fi
    else
        echo -e "${RED}✗ 無法獲取 CPU 指標${NC}"
        return 1
    fi
}

# 檢查 zPages 狀態
check_zpages_status() {
    echo -e "${BLUE}檢查 zPages 內部狀態...${NC}"
    
    if curl -s -f "http://${COLLECTOR_HOST}:${ZPAGES_PORT}" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ zPages 可用${NC}"
        echo "訪問 http://${COLLECTOR_HOST}:${ZPAGES_PORT} 查看詳細內部狀態"
        
        # 檢查特定的 zPages 端點
        local zpages_endpoints=("tracez" "pipelinez" "servicez" "extensionz")
        
        for endpoint in "${zpages_endpoints[@]}"; do
            if curl -s -f "http://${COLLECTOR_HOST}:${ZPAGES_PORT}/${endpoint}" > /dev/null 2>&1; then
                echo -e "${GREEN}  ✓ ${endpoint} 端點可用${NC}"
            else
                echo -e "${YELLOW}  ⚠ ${endpoint} 端點不可用${NC}"
            fi
        done
    else
        echo -e "${RED}✗ zPages 不可用${NC}"
        return 1
    fi
}

# 檢查效能分析端點
check_pprof_status() {
    echo -e "${BLUE}檢查效能分析端點...${NC}"
    
    if curl -s -f "http://${COLLECTOR_HOST}:${PPROF_PORT}/debug/pprof/" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ pprof 效能分析端點可用${NC}"
        echo "訪問 http://${COLLECTOR_HOST}:${PPROF_PORT}/debug/pprof/ 進行效能分析"
        
        # 檢查可用的 profile 類型
        local pprof_response
        if pprof_response=$(curl -s "http://${COLLECTOR_HOST}:${PPROF_PORT}/debug/pprof/" 2>/dev/null); then
            echo "可用的 profile 類型:"
            echo "$pprof_response" | grep -E "(heap|goroutine|threadcreate|block|mutex)" | head -5
        fi
    else
        echo -e "${YELLOW}⚠ pprof 效能分析端點不可用${NC}"
    fi
}

# 檢查管道狀態
check_pipeline_status() {
    echo -e "${BLUE}檢查管道狀態...${NC}"
    
    local metrics_response
    if metrics_response=$(curl -s "http://${COLLECTOR_HOST}:${METRICS_PORT}/metrics" 2>/dev/null); then
        # 檢查各個管道的處理量
        echo -e "${BLUE}檢查管道處理量...${NC}"
        
        # 指標管道
        local metrics_pipeline
        metrics_pipeline=$(echo "$metrics_response" | grep -E "otelcol_receiver_accepted_metric_points" | head -3)
        if [ -n "$metrics_pipeline" ]; then
            echo -e "${GREEN}✓ 指標管道活躍${NC}"
            echo "$metrics_pipeline"
        fi
        
        # 追蹤管道
        local traces_pipeline
        traces_pipeline=$(echo "$metrics_response" | grep -E "otelcol_receiver_accepted_spans" | head -3)
        if [ -n "$traces_pipeline" ]; then
            echo -e "${GREEN}✓ 追蹤管道活躍${NC}"
            echo "$traces_pipeline"
        fi
        
        # 日誌管道
        local logs_pipeline
        logs_pipeline=$(echo "$metrics_response" | grep -E "otelcol_receiver_accepted_log_records" | head -3)
        if [ -n "$logs_pipeline" ]; then
            echo -e "${GREEN}✓ 日誌管道活躍${NC}"
            echo "$logs_pipeline"
        fi
    else
        echo -e "${RED}✗ 無法獲取管道狀態${NC}"
        return 1
    fi
}

# 生成健康報告
generate_health_report() {
    echo -e "${BLUE}生成健康報告...${NC}"
    
    local report_file="/tmp/otel-collector-health-report-$(date +%Y%m%d-%H%M%S).txt"
    
    {
        echo "OpenTelemetry Collector 健康報告"
        echo "生成時間: $(date)"
        echo "========================================"
        echo
        
        echo "基本健康狀態:"
        curl -s "http://${COLLECTOR_HOST}:${HEALTH_CHECK_PORT}/health" 2>/dev/null || echo "健康檢查失敗"
        echo
        
        echo "關鍵指標摘要:"
        curl -s "http://${COLLECTOR_HOST}:${METRICS_PORT}/metrics" 2>/dev/null | grep -E "(otelcol_receiver_|otelcol_processor_|otelcol_exporter_)" | head -20
        echo
        
        echo "記憶體和 CPU 使用:"
        curl -s "http://${COLLECTOR_HOST}:${METRICS_PORT}/metrics" 2>/dev/null | grep -E "(process_resident_memory_bytes|process_cpu_seconds_total)" | head -10
        
    } > "$report_file"
    
    echo -e "${GREEN}✓ 健康報告已生成: $report_file${NC}"
}

# 主函數
main() {
    echo "開始 OpenTelemetry Collector 自監控健康檢查..."
    echo
    
    local exit_code=0
    
    check_health_status || exit_code=1
    echo
    
    check_self_monitoring_metrics || exit_code=1
    echo
    
    check_memory_usage || exit_code=1
    echo
    
    check_cpu_usage || exit_code=1
    echo
    
    check_zpages_status || exit_code=1
    echo
    
    check_pprof_status
    echo
    
    check_pipeline_status || exit_code=1
    echo
    
    generate_health_report
    echo
    
    if [ $exit_code -eq 0 ]; then
        echo -e "${GREEN}✓ 所有健康檢查通過！${NC}"
    else
        echo -e "${RED}✗ 部分健康檢查失敗，請檢查上述輸出${NC}"
    fi
    
    echo "結束時間: $(date)"
    return $exit_code
}

# 執行主函數
main "$@"