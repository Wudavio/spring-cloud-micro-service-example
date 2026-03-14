#!/bin/bash

# OpenTelemetry 敏感資料過濾腳本
# 實作敏感資訊檢測和脫敏功能

set -euo pipefail

# 腳本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/data-privacy-config.yaml"
LOG_FILE="${SCRIPT_DIR}/data-privacy-filter.log"
STATS_FILE="${SCRIPT_DIR}/data-privacy-stats.json"

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日誌函數
log() {
    local level=$1
    shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "${timestamp} [${level}] ${message}" | tee -a "${LOG_FILE}"
}

log_info() {
    log "INFO" "$*"
}

log_warn() {
    log "WARN" "${YELLOW}$*${NC}"
}

log_error() {
    log "ERROR" "${RED}$*${NC}"
}

log_success() {
    log "SUCCESS" "${GREEN}$*${NC}"
}

# 檢查依賴
check_dependencies() {
    log_info "檢查依賴套件..."
    
    local deps=("yq" "jq" "grep" "sed" "awk")
    local missing_deps=()
    
    for dep in "${deps[@]}"; do
        if ! command -v "$dep" &> /dev/null; then
            missing_deps+=("$dep")
        fi
    done
    
    if [ ${#missing_deps[@]} -ne 0 ]; then
        log_error "缺少依賴套件: ${missing_deps[*]}"
        log_info "請安裝缺少的套件後重新執行"
        exit 1
    fi
    
    log_success "所有依賴套件已安裝"
}

# 載入配置
load_config() {
    log_info "載入資料隱私配置..."
    
    if [ ! -f "$CONFIG_FILE" ]; then
        log_error "配置檔案不存在: $CONFIG_FILE"
        exit 1
    fi
    
    # 驗證配置檔案格式
    if ! yq eval '.' "$CONFIG_FILE" > /dev/null 2>&1; then
        log_error "配置檔案格式錯誤: $CONFIG_FILE"
        exit 1
    fi
    
    log_success "配置檔案載入成功"
}

# 初始化統計資料
init_stats() {
    cat > "$STATS_FILE" << EOF
{
  "start_time": "$(date -Iseconds)",
  "total_processed": 0,
  "sensitive_data_detected": 0,
  "data_masked": 0,
  "patterns_matched": {},
  "compliance_violations": 0,
  "last_updated": "$(date -Iseconds)"
}
EOF
}

# 更新統計資料
update_stats() {
    local pattern_type="$1"
    local count="${2:-1}"
    
    # 使用 jq 更新統計資料
    jq --arg pattern "$pattern_type" --argjson count "$count" '
        .sensitive_data_detected += $count |
        .data_masked += $count |
        .patterns_matched[$pattern] = (.patterns_matched[$pattern] // 0) + $count |
        .last_updated = now | strftime("%Y-%m-%dT%H:%M:%S%z")
    ' "$STATS_FILE" > "${STATS_FILE}.tmp" && mv "${STATS_FILE}.tmp" "$STATS_FILE"
}

# 敏感資料檢測函數
detect_sensitive_data() {
    local input_text="$1"
    local data_type="$2"  # metrics, traces, logs
    local detected_patterns=()
    
    # 從配置檔案讀取敏感資料模式
    local patterns
    patterns=$(yq eval '.sensitive_data_patterns | to_entries | .[] | .key + ":" + (.value[] | .pattern)' "$CONFIG_FILE")
    
    while IFS=':' read -r pattern_type pattern; do
        if echo "$input_text" | grep -qE "$pattern"; then
            detected_patterns+=("$pattern_type")
            log_warn "檢測到敏感資料: $pattern_type 在 $data_type 中"
            update_stats "$pattern_type"
        fi
    done <<< "$patterns"
    
    printf '%s\n' "${detected_patterns[@]}"
}

# 資料脫敏函數
mask_sensitive_data() {
    local input_text="$1"
    local pattern_type="$2"
    local masking_strategy="$3"
    
    case "$masking_strategy" in
        "full_mask")
            local replacement
            replacement=$(yq eval '.masking_strategies.full_mask.replacement' "$CONFIG_FILE")
            echo "$input_text" | sed -E 's/[^[:space:]]+/'"$replacement"'/g'
            ;;
        "partial_mask")
            # 實作部分遮蔽邏輯
            echo "$input_text" | sed -E 's/(.{2}).*(.{2})/\1***\2/g'
            ;;
        "hash_mask")
            # 實作雜湊遮蔽邏輯
            local salt
            salt=$(yq eval '.masking_strategies.hash_mask.salt' "$CONFIG_FILE")
            echo -n "$input_text$salt" | sha256sum | cut -d' ' -f1 | sed 's/^/HASH_/'
            ;;
        *)
            log_warn "未知的脫敏策略: $masking_strategy"
            echo "***UNKNOWN_STRATEGY***"
            ;;
    esac
}

# 處理指標資料
process_metrics() {
    local metrics_data="$1"
    local output_file="$2"
    
    log_info "處理指標資料..."
    
    # 檢查是否啟用指標過濾
    local metrics_enabled
    metrics_enabled=$(yq eval '.filters.metrics.enabled' "$CONFIG_FILE")
    
    if [ "$metrics_enabled" != "true" ]; then
        log_info "指標過濾已停用，跳過處理"
        echo "$metrics_data" > "$output_file"
        return
    fi
    
    local processed_data="$metrics_data"
    local scan_labels
    scan_labels=$(yq eval '.filters.metrics.scan_labels' "$CONFIG_FILE")
    
    if [ "$scan_labels" = "true" ]; then
        # 掃描指標標籤中的敏感資料
        local detected_patterns
        detected_patterns=$(detect_sensitive_data "$processed_data" "metrics")
        
        if [ -n "$detected_patterns" ]; then
            # 應用脱敏策略
            while read -r pattern; do
                [ -z "$pattern" ] && continue
                local strategy
                strategy=$(yq eval ".masking_strategies | to_entries | .[] | select(.value.apply_to[] == \"$pattern\") | .key" "$CONFIG_FILE" | head -1)
                if [ -n "$strategy" ]; then
                    processed_data=$(mask_sensitive_data "$processed_data" "$pattern" "$strategy")
                fi
            done <<< "$detected_patterns"
        fi
    fi
    
    echo "$processed_data" > "$output_file"
    log_success "指標資料處理完成"
}

# 處理追蹤資料
process_traces() {
    local traces_data="$1"
    local output_file="$2"
    
    log_info "處理追蹤資料..."
    
    # 檢查是否啟用追蹤過濾
    local traces_enabled
    traces_enabled=$(yq eval '.filters.traces.enabled' "$CONFIG_FILE")
    
    if [ "$traces_enabled" != "true" ]; then
        log_info "追蹤過濾已停用，跳過處理"
        echo "$traces_data" > "$output_file"
        return
    fi
    
    local processed_data="$traces_data"
    
    # 處理 HTTP 標頭
    local sensitive_headers
    sensitive_headers=$(yq eval '.special_rules.http_headers.sensitive_headers[]' "$CONFIG_FILE")
    
    while read -r header; do
        [ -z "$header" ] && continue
        # 移除敏感的 HTTP 標頭
        processed_data=$(echo "$processed_data" | sed -E "s/\"$header\":[^,}]+/\"$header\":\"***REDACTED**\"/gi")
    done <<< "$sensitive_headers"
    
    # 處理 URL 參數
    local sensitive_params
    sensitive_params=$(yq eval '.special_rules.url_parameters.sensitive_params[]' "$CONFIG_FILE")
    
    while read -r param; do
        [ -z "$param" ] && continue
        # 移除敏感的 URL 參數
        processed_data=$(echo "$processed_data" | sed -E "s/[?&]$param=[^&]*/\&$param=***REDACTED***/gi")
    done <<< "$sensitive_params"
    
    echo "$processed_data" > "$output_file"
    log_success "追蹤資料處理完成"
}

# 處理日誌資料
process_logs() {
    local logs_data="$1"
    local output_file="$2"
    
    log_info "處理日誌資料..."
    
    # 檢查是否啟用日誌過濾
    local logs_enabled
    logs_enabled=$(yq eval '.filters.logs.enabled' "$CONFIG_FILE")
    
    if [ "$logs_enabled" != "true" ]; then
        log_info "日誌過濾已停用，跳過處理"
        echo "$logs_data" > "$output_file"
        return
    fi
    
    local processed_data="$logs_data"
    local scan_body
    scan_body=$(yq eval '.filters.logs.scan_body' "$CONFIG_FILE")
    
    if [ "$scan_body" = "true" ]; then
        # 掃描日誌內容中的敏感資料
        local detected_patterns
        detected_patterns=$(detect_sensitive_data "$processed_data" "logs")
        
        if [ -n "$detected_patterns" ]; then
            # 應用脱敏策略
            while read -r pattern; do
                [ -z "$pattern" ] && continue
                local strategy
                strategy=$(yq eval ".masking_strategies | to_entries | .[] | select(.value.apply_to[] == \"$pattern\") | .key" "$CONFIG_FILE" | head -1)
                if [ -n "$strategy" ]; then
                    processed_data=$(mask_sensitive_data "$processed_data" "$pattern" "$strategy")
                fi
            done <<< "$detected_patterns"
        fi
    fi
    
    echo "$processed_data" > "$output_file"
    log_success "日誌資料處理完成"
}

# 生成合規性報告
generate_compliance_report() {
    local report_file="${SCRIPT_DIR}/compliance-report-$(date +%Y%m%d-%H%M%S).json"
    
    log_info "生成合規性報告..."
    
    local total_detected
    total_detected=$(jq -r '.sensitive_data_detected' "$STATS_FILE")
    
    local total_masked
    total_masked=$(jq -r '.data_masked' "$STATS_FILE")
    
    local compliance_score
    if [ "$total_detected" -eq 0 ]; then
        compliance_score=100
    else
        compliance_score=$(echo "scale=2; $total_masked * 100 / $total_detected" | bc)
    fi
    
    cat > "$report_file" << EOF
{
  "report_generated": "$(date -Iseconds)",
  "compliance_summary": {
    "total_data_processed": $(jq -r '.total_processed' "$STATS_FILE"),
    "sensitive_data_detected": $total_detected,
    "data_successfully_masked": $total_masked,
    "compliance_score": $compliance_score,
    "compliance_status": "$([ $(echo "$compliance_score >= 95" | bc) -eq 1 ] && echo "COMPLIANT" || echo "NON_COMPLIANT")"
  },
  "pattern_breakdown": $(jq -r '.patterns_matched' "$STATS_FILE"),
  "recommendations": [
    "定期審查敏感資料檢測規則",
    "監控合規性分數變化趨勢",
    "確保所有敏感資料都有適當的脫敏策略",
    "定期更新資料隱私配置"
  ],
  "next_review_date": "$(date -d '+30 days' -Iseconds)"
}
EOF
    
    log_success "合規性報告已生成: $report_file"
    
    # 如果合規性分數低於 95%，發出警告
    if [ $(echo "$compliance_score < 95" | bc) -eq 1 ]; then
        log_warn "合規性分數低於 95%: $compliance_score%"
        log_warn "建議檢查脫敏策略配置"
    fi
}

# 監控敏感資料檢測
monitor_detection_rate() {
    log_info "監控敏感資料檢測率..."
    
    local high_threshold
    high_threshold=$(yq eval '.monitoring.alert_thresholds.high_detection_rate' "$CONFIG_FILE")
    
    local current_rate
    current_rate=$(jq -r '.sensitive_data_detected' "$STATS_FILE")
    
    if [ "$current_rate" -gt "$high_threshold" ]; then
        log_warn "敏感資料檢測率過高: $current_rate (閾值: $high_threshold)"
        log_warn "建議檢查資料來源和檢測規則"
    fi
}

# 清理過期的統計資料和報告
cleanup_old_files() {
    log_info "清理過期檔案..."
    
    # 清理 7 天前的報告檔案
    find "$SCRIPT_DIR" -name "compliance-report-*.json" -mtime +7 -delete 2>/dev/null || true
    
    # 清理 30 天前的日誌檔案
    find "$SCRIPT_DIR" -name "*.log" -mtime +30 -delete 2>/dev/null || true
    
    log_success "過期檔案清理完成"
}

# 主函數
main() {
    local command="${1:-help}"
    
    case "$command" in
        "init")
            log_info "初始化資料隱私過濾器..."
            check_dependencies
            load_config
            init_stats
            log_success "資料隱私過濾器初始化完成"
            ;;
        "process")
            local data_type="${2:-}"
            local input_file="${3:-}"
            local output_file="${4:-}"
            
            if [ -z "$data_type" ] || [ -z "$input_file" ] || [ -z "$output_file" ]; then
                log_error "使用方式: $0 process <metrics|traces|logs> <input_file> <output_file>"
                exit 1
            fi
            
            if [ ! -f "$input_file" ]; then
                log_error "輸入檔案不存在: $input_file"
                exit 1
            fi
            
            check_dependencies
            load_config
            
            local input_data
            input_data=$(cat "$input_file")
            
            case "$data_type" in
                "metrics")
                    process_metrics "$input_data" "$output_file"
                    ;;
                "traces")
                    process_traces "$input_data" "$output_file"
                    ;;
                "logs")
                    process_logs "$input_data" "$output_file"
                    ;;
                *)
                    log_error "不支援的資料類型: $data_type"
                    exit 1
                    ;;
            esac
            ;;
        "report")
            check_dependencies
            load_config
            generate_compliance_report
            ;;
        "monitor")
            check_dependencies
            load_config
            monitor_detection_rate
            ;;
        "cleanup")
            cleanup_old_files
            ;;
        "help"|*)
            echo "OpenTelemetry 資料隱私過濾器"
            echo ""
            echo "使用方式:"
            echo "  $0 init                                    # 初始化過濾器"
            echo "  $0 process <type> <input> <output>         # 處理資料"
            echo "  $0 report                                  # 生成合規性報告"
            echo "  $0 monitor                                 # 監控檢測率"
            echo "  $0 cleanup                                 # 清理過期檔案"
            echo "  $0 help                                    # 顯示說明"
            echo ""
            echo "資料類型:"
            echo "  metrics  # 處理指標資料"
            echo "  traces   # 處理追蹤資料"
            echo "  logs     # 處理日誌資料"
            ;;
    esac
}

# 執行主函數
main "$@"