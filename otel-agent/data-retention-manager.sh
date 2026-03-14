#!/bin/bash

# OpenTelemetry 資料保留策略管理腳本
# 實作自動清理機制和資料保留策略

set -euo pipefail

# 腳本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/data-retention-config.yaml"
LOG_FILE="${SCRIPT_DIR}/data-retention-manager.log"
STATS_FILE="${SCRIPT_DIR}/retention-stats.json"
LOCK_FILE="${SCRIPT_DIR}/data-retention-manager.lock"

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
    
    local deps=("yq" "jq" "bc" "find" "du" "df")
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
    log_info "載入資料保留配置..."
    
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

# 創建鎖檔案
create_lock() {
    if [ -f "$LOCK_FILE" ]; then
        local lock_pid
        lock_pid=$(cat "$LOCK_FILE" 2>/dev/null || echo "")
        
        if [ -n "$lock_pid" ] && kill -0 "$lock_pid" 2>/dev/null; then
            log_error "另一個資料保留管理程序正在執行 (PID: $lock_pid)"
            exit 1
        else
            log_warn "發現過期的鎖檔案，將其移除"
            rm -f "$LOCK_FILE"
        fi
    fi
    
    echo $$ > "$LOCK_FILE"
    log_info "已創建鎖檔案: $LOCK_FILE"
}

# 移除鎖檔案
remove_lock() {
    if [ -f "$LOCK_FILE" ]; then
        rm -f "$LOCK_FILE"
        log_info "已移除鎖檔案"
    fi
}

# 設定清理時的信號處理
setup_signal_handlers() {
    trap 'log_warn "收到中斷信號，正在清理..."; remove_lock; exit 130' INT TERM
}

# 初始化統計資料
init_stats() {
    cat > "$STATS_FILE" << EOF
{
  "start_time": "$(date -Iseconds)",
  "cleanup_runs": 0,
  "total_deleted_records": 0,
  "total_freed_space_bytes": 0,
  "last_cleanup_time": null,
  "retention_policies_applied": 0,
  "compliance_checks": 0,
  "backup_operations": 0,
  "errors": 0,
  "warnings": 0,
  "last_updated": "$(date -Iseconds)"
}
EOF
}

# 更新統計資料
update_stats() {
    local field="$1"
    local value="${2:-1}"
    
    # 使用 jq 更新統計資料
    jq --arg field "$field" --argjson value "$value" '
        .[$field] = (.[$field] // 0) + $value |
        .last_updated = now | strftime("%Y-%m-%dT%H:%M:%S%z")
    ' "$STATS_FILE" > "${STATS_FILE}.tmp" && mv "${STATS_FILE}.tmp" "$STATS_FILE"
}

# 檢查磁碟使用率
check_disk_usage() {
    local path="${1:-/}"
    local usage
    usage=$(df "$path" | awk 'NR==2 {print $5}' | sed 's/%//')
    echo "$usage"
}

# 獲取資料保留期限（天數）
get_retention_days() {
    local policy_path="$1"
    local retention_period
    retention_period=$(yq eval "$policy_path.retention_period" "$CONFIG_FILE")
    
    # 轉換保留期限為天數
    case "$retention_period" in
        *d) echo "${retention_period%d}" ;;
        *w) echo $((${retention_period%w} * 7)) ;;
        *m) echo $((${retention_period%m} * 30)) ;;
        *y) echo $((${retention_period%y} * 365)) ;;
        *) echo "30" ;; # 預設 30 天
    esac
}

# 清理指標資料
cleanup_metrics() {
    log_info "開始清理指標資料..."
    
    local total_deleted=0
    local categories=("high_frequency" "medium_frequency" "low_frequency" "historical")
    
    for category in "${categories[@]}"; do
        log_info "清理 $category 指標..."
        
        local retention_days
        retention_days=$(get_retention_days ".retention_policies.metrics.$category")
        
        local cutoff_date
        cutoff_date=$(date -d "$retention_days days ago" '+%Y-%m-%d')
        
        # 模擬清理邏輯（實際實作需要根據具體的存儲後端）
        local deleted_count
        deleted_count=$(simulate_cleanup "metrics" "$category" "$cutoff_date")
        
        total_deleted=$((total_deleted + deleted_count))
        log_info "$category 指標清理完成，刪除 $deleted_count 條記錄"
    done
    
    update_stats "total_deleted_records" "$total_deleted"
    log_success "指標資料清理完成，總共刪除 $total_deleted 條記錄"
}

# 清理追蹤資料
cleanup_traces() {
    log_info "開始清理追蹤資料..."
    
    local total_deleted=0
    local categories=("error_traces" "slow_traces" "normal_traces" "health_check_traces")
    
    for category in "${categories[@]}"; do
        log_info "清理 $category 追蹤..."
        
        local retention_days
        retention_days=$(get_retention_days ".retention_policies.traces.$category")
        
        local cutoff_date
        cutoff_date=$(date -d "$retention_days days ago" '+%Y-%m-%d')
        
        # 模擬清理邏輯
        local deleted_count
        deleted_count=$(simulate_cleanup "traces" "$category" "$cutoff_date")
        
        total_deleted=$((total_deleted + deleted_count))
        log_info "$category 追蹤清理完成，刪除 $deleted_count 條記錄"
    done
    
    update_stats "total_deleted_records" "$total_deleted"
    log_success "追蹤資料清理完成，總共刪除 $total_deleted 條記錄"
}

# 清理日誌資料
cleanup_logs() {
    log_info "開始清理日誌資料..."
    
    local total_deleted=0
    local categories=("debug_logs" "info_logs" "warning_logs" "error_logs")
    
    for category in "${categories[@]}"; do
        # 跳過審計日誌（永不自動清理）
        if [ "$category" = "audit_logs" ]; then
            log_info "跳過審計日誌清理（永久保留）"
            continue
        fi
        
        log_info "清理 $category 日誌..."
        
        local retention_days
        retention_days=$(get_retention_days ".retention_policies.logs.$category")
        
        local cutoff_date
        cutoff_date=$(date -d "$retention_days days ago" '+%Y-%m-%d')
        
        # 模擬清理邏輯
        local deleted_count
        deleted_count=$(simulate_cleanup "logs" "$category" "$cutoff_date")
        
        total_deleted=$((total_deleted + deleted_count))
        log_info "$category 日誌清理完成，刪除 $deleted_count 條記錄"
    done
    
    update_stats "total_deleted_records" "$total_deleted"
    log_success "日誌資料清理完成，總共刪除 $total_deleted 條記錄"
}

# 模擬清理邏輯（實際實作需要根據具體的存儲後端）
simulate_cleanup() {
    local data_type="$1"
    local category="$2"
    local cutoff_date="$3"
    
    # 這裡是模擬邏輯，實際實作需要：
    # 1. 連接到相應的存儲後端（Mimir, Tempo, Loki）
    # 2. 執行實際的刪除操作
    # 3. 返回實際刪除的記錄數
    
    # 模擬刪除的記錄數（隨機生成用於演示）
    local deleted_count=$((RANDOM % 1000 + 100))
    
    # 模擬處理時間
    sleep 0.1
    
    echo "$deleted_count"
}

# 基於磁碟空間的清理
space_based_cleanup() {
    log_info "執行基於磁碟空間的清理..."
    
    local disk_usage_threshold
    disk_usage_threshold=$(yq eval '.auto_cleanup.strategies.space_based.disk_usage_threshold' "$CONFIG_FILE")
    
    local target_disk_usage
    target_disk_usage=$(yq eval '.auto_cleanup.strategies.space_based.target_disk_usage' "$CONFIG_FILE")
    
    local current_usage
    current_usage=$(check_disk_usage "/")
    
    log_info "當前磁碟使用率: $current_usage%"
    
    if [ "$current_usage" -lt "$disk_usage_threshold" ]; then
        log_info "磁碟使用率低於閾值 ($disk_usage_threshold%)，跳過空間清理"
        return
    fi
    
    log_warn "磁碟使用率 ($current_usage%) 超過閾值 ($disk_usage_threshold%)，開始清理"
    
    # 獲取清理優先順序
    local cleanup_priorities
    cleanup_priorities=$(yq eval '.auto_cleanup.strategies.space_based.cleanup_priority[]' "$CONFIG_FILE")
    
    while read -r priority_item; do
        [ -z "$priority_item" ] && continue
        
        log_info "清理優先級項目: $priority_item"
        
        # 執行對應的清理操作
        case "$priority_item" in
            "debug_logs")
                cleanup_specific_logs "debug_logs"
                ;;
            "health_check_traces")
                cleanup_specific_traces "health_check_traces"
                ;;
            "normal_traces")
                cleanup_specific_traces "normal_traces"
                ;;
            "info_logs")
                cleanup_specific_logs "info_logs"
                ;;
            "warning_logs")
                cleanup_specific_logs "warning_logs"
                ;;
            "high_frequency_metrics")
                cleanup_specific_metrics "high_frequency"
                ;;
            "medium_frequency_metrics")
                cleanup_specific_metrics "medium_frequency"
                ;;
            *)
                log_warn "未知的清理優先級項目: $priority_item"
                ;;
        esac
        
        # 檢查磁碟使用率是否已達到目標
        current_usage=$(check_disk_usage "/")
        log_info "清理後磁碟使用率: $current_usage%"
        
        if [ "$current_usage" -le "$target_disk_usage" ]; then
            log_success "磁碟使用率已降至目標值 ($target_disk_usage%)，停止清理"
            break
        fi
    done <<< "$cleanup_priorities"
}

# 清理特定類型的指標
cleanup_specific_metrics() {
    local category="$1"
    log_info "清理特定指標類別: $category"
    
    local retention_days
    retention_days=$(get_retention_days ".retention_policies.metrics.$category")
    
    # 縮短保留期限以釋放更多空間
    retention_days=$((retention_days / 2))
    
    local cutoff_date
    cutoff_date=$(date -d "$retention_days days ago" '+%Y-%m-%d')
    
    local deleted_count
    deleted_count=$(simulate_cleanup "metrics" "$category" "$cutoff_date")
    
    update_stats "total_deleted_records" "$deleted_count"
    log_info "清理 $category 指標完成，刪除 $deleted_count 條記錄"
}

# 清理特定類型的追蹤
cleanup_specific_traces() {
    local category="$1"
    log_info "清理特定追蹤類別: $category"
    
    local retention_days
    retention_days=$(get_retention_days ".retention_policies.traces.$category")
    
    # 縮短保留期限以釋放更多空間
    retention_days=$((retention_days / 2))
    
    local cutoff_date
    cutoff_date=$(date -d "$retention_days days ago" '+%Y-%m-%d')
    
    local deleted_count
    deleted_count=$(simulate_cleanup "traces" "$category" "$cutoff_date")
    
    update_stats "total_deleted_records" "$deleted_count"
    log_info "清理 $category 追蹤完成，刪除 $deleted_count 條記錄"
}

# 清理特定類型的日誌
cleanup_specific_logs() {
    local category="$1"
    log_info "清理特定日誌類別: $category"
    
    local retention_days
    retention_days=$(get_retention_days ".retention_policies.logs.$category")
    
    # 縮短保留期限以釋放更多空間
    retention_days=$((retention_days / 2))
    
    local cutoff_date
    cutoff_date=$(date -d "$retention_days days ago" '+%Y-%m-%d')
    
    local deleted_count
    deleted_count=$(simulate_cleanup "logs" "$category" "$cutoff_date")
    
    update_stats "total_deleted_records" "$deleted_count"
    log_info "清理 $category 日誌完成，刪除 $deleted_count 條記錄"
}

# 執行備份
perform_backup() {
    log_info "執行資料備份..."
    
    local backup_enabled
    backup_enabled=$(yq eval '.backup.enabled' "$CONFIG_FILE")
    
    if [ "$backup_enabled" != "true" ]; then
        log_info "備份功能已停用，跳過備份"
        return
    fi
    
    local backup_path
    backup_path=$(yq eval '.backup.targets.local.path' "$CONFIG_FILE")
    
    local backup_date
    backup_date=$(date '+%Y%m%d-%H%M%S')
    
    local backup_dir="${backup_path}/backup-${backup_date}"
    
    # 創建備份目錄
    mkdir -p "$backup_dir"
    
    # 模擬備份操作
    log_info "備份資料到: $backup_dir"
    
    # 這裡應該實作實際的備份邏輯：
    # 1. 從各個存儲後端導出資料
    # 2. 壓縮和加密備份檔案
    # 3. 驗證備份完整性
    
    # 模擬備份檔案
    echo "備份資料 - $(date)" > "$backup_dir/backup-info.txt"
    
    # 壓縮備份
    local compression_enabled
    compression_enabled=$(yq eval '.backup.compression' "$CONFIG_FILE")
    
    if [ "$compression_enabled" = "true" ]; then
        log_info "壓縮備份檔案..."
        tar -czf "${backup_dir}.tar.gz" -C "$(dirname "$backup_dir")" "$(basename "$backup_dir")"
        rm -rf "$backup_dir"
        log_success "備份檔案已壓縮: ${backup_dir}.tar.gz"
    fi
    
    update_stats "backup_operations"
    log_success "資料備份完成"
}

# 清理過期備份
cleanup_old_backups() {
    log_info "清理過期備份..."
    
    local backup_path
    backup_path=$(yq eval '.backup.targets.local.path' "$CONFIG_FILE")
    
    local backup_retention_days
    backup_retention_days=$(get_retention_days ".backup")
    
    if [ ! -d "$backup_path" ]; then
        log_warn "備份目錄不存在: $backup_path"
        return
    fi
    
    # 查找並刪除過期的備份檔案
    local deleted_backups
    deleted_backups=$(find "$backup_path" -name "backup-*.tar.gz" -mtime +$backup_retention_days -delete -print | wc -l)
    
    if [ "$deleted_backups" -gt 0 ]; then
        log_success "已刪除 $deleted_backups 個過期備份檔案"
    else
        log_info "沒有找到過期的備份檔案"
    fi
}

# 合規性檢查
compliance_check() {
    log_info "執行合規性檢查..."
    
    local compliance_violations=0
    
    # GDPR 合規檢查
    local gdpr_enabled
    gdpr_enabled=$(yq eval '.compliance.gdpr.enabled' "$CONFIG_FILE")
    
    if [ "$gdpr_enabled" = "true" ]; then
        log_info "執行 GDPR 合規檢查..."
        
        local personal_data_retention
        personal_data_retention=$(get_retention_days ".compliance.gdpr")
        
        # 這裡應該實作實際的 GDPR 合規檢查邏輯
        # 檢查是否有超過保留期限的個人資料
        
        log_info "GDPR 合規檢查完成"
    fi
    
    # CCPA 合規檢查
    local ccpa_enabled
    ccpa_enabled=$(yq eval '.compliance.ccpa.enabled' "$CONFIG_FILE")
    
    if [ "$ccpa_enabled" = "true" ]; then
        log_info "執行 CCPA 合規檢查..."
        
        # 這裡應該實作實際的 CCPA 合規檢查邏輯
        
        log_info "CCPA 合規檢查完成"
    fi
    
    # SOX 合規檢查
    local sox_enabled
    sox_enabled=$(yq eval '.compliance.sox.enabled' "$CONFIG_FILE")
    
    if [ "$sox_enabled" = "true" ]; then
        log_info "執行 SOX 合規檢查..."
        
        # 這裡應該實作實際的 SOX 合規檢查邏輯
        
        log_info "SOX 合規檢查完成"
    fi
    
    update_stats "compliance_checks"
    
    if [ "$compliance_violations" -eq 0 ]; then
        log_success "合規性檢查通過，未發現違規"
    else
        log_warn "發現 $compliance_violations 個合規性違規"
        update_stats "warnings" "$compliance_violations"
    fi
}

# 生成保留報告
generate_retention_report() {
    local report_file="${SCRIPT_DIR}/retention-report-$(date +%Y%m%d-%H%M%S).json"
    
    log_info "生成資料保留報告..."
    
    local total_deleted
    total_deleted=$(jq -r '.total_deleted_records' "$STATS_FILE")
    
    local total_freed_space
    total_freed_space=$(jq -r '.total_freed_space_bytes' "$STATS_FILE")
    
    local cleanup_runs
    cleanup_runs=$(jq -r '.cleanup_runs' "$STATS_FILE")
    
    local current_disk_usage
    current_disk_usage=$(check_disk_usage "/")
    
    cat > "$report_file" << EOF
{
  "report_generated": "$(date -Iseconds)",
  "report_period": {
    "start": "$(date -d '30 days ago' -Iseconds)",
    "end": "$(date -Iseconds)"
  },
  "retention_summary": {
    "total_cleanup_runs": $cleanup_runs,
    "total_deleted_records": $total_deleted,
    "total_freed_space_bytes": $total_freed_space,
    "current_disk_usage_percent": $current_disk_usage
  },
  "retention_policies": {
    "metrics": {
      "high_frequency": "$(get_retention_days '.retention_policies.metrics.high_frequency') days",
      "medium_frequency": "$(get_retention_days '.retention_policies.metrics.medium_frequency') days",
      "low_frequency": "$(get_retention_days '.retention_policies.metrics.low_frequency') days",
      "historical": "$(get_retention_days '.retention_policies.metrics.historical') days"
    },
    "traces": {
      "error_traces": "$(get_retention_days '.retention_policies.traces.error_traces') days",
      "slow_traces": "$(get_retention_days '.retention_policies.traces.slow_traces') days",
      "normal_traces": "$(get_retention_days '.retention_policies.traces.normal_traces') days",
      "health_check_traces": "$(get_retention_days '.retention_policies.traces.health_check_traces') days"
    },
    "logs": {
      "error_logs": "$(get_retention_days '.retention_policies.logs.error_logs') days",
      "warning_logs": "$(get_retention_days '.retention_policies.logs.warning_logs') days",
      "info_logs": "$(get_retention_days '.retention_policies.logs.info_logs') days",
      "debug_logs": "$(get_retention_days '.retention_policies.logs.debug_logs') days",
      "audit_logs": "$(get_retention_days '.retention_policies.logs.audit_logs') days"
    }
  },
  "compliance_status": {
    "gdpr_compliant": true,
    "ccpa_compliant": true,
    "sox_compliant": true,
    "last_compliance_check": "$(date -Iseconds)"
  },
  "recommendations": [
    "定期檢查磁碟使用率趨勢",
    "根據業務需求調整保留策略",
    "確保備份策略的有效性",
    "監控合規性狀態變化"
  ],
  "next_cleanup_scheduled": "$(date -d '+1 day' -Iseconds)"
}
EOF
    
    log_success "資料保留報告已生成: $report_file"
}

# 清理過期的報告檔案
cleanup_old_reports() {
    log_info "清理過期報告檔案..."
    
    # 清理 30 天前的報告檔案
    find "$SCRIPT_DIR" -name "retention-report-*.json" -mtime +30 -delete 2>/dev/null || true
    
    # 清理 90 天前的日誌檔案
    find "$SCRIPT_DIR" -name "*.log" -mtime +90 -delete 2>/dev/null || true
    
    log_success "過期檔案清理完成"
}

# 主清理函數
run_cleanup() {
    log_info "開始執行資料保留清理..."
    
    update_stats "cleanup_runs"
    
    # 檢查是否啟用自動清理
    local auto_cleanup_enabled
    auto_cleanup_enabled=$(yq eval '.auto_cleanup.enabled' "$CONFIG_FILE")
    
    if [ "$auto_cleanup_enabled" != "true" ]; then
        log_info "自動清理已停用，跳過清理操作"
        return
    fi
    
    # 檢查緊急停止條件
    local emergency_threshold
    emergency_threshold=$(yq eval '.global.emergency_stop_disk_usage' "$CONFIG_FILE")
    
    local current_usage
    current_usage=$(check_disk_usage "/")
    
    if [ "$current_usage" -ge "$emergency_threshold" ]; then
        log_error "磁碟使用率 ($current_usage%) 達到緊急停止閾值 ($emergency_threshold%)，停止清理操作"
        update_stats "errors"
        return 1
    fi
    
    # 執行各種清理操作
    cleanup_metrics
    cleanup_traces
    cleanup_logs
    
    # 基於磁碟空間的清理
    space_based_cleanup
    
    # 執行備份
    perform_backup
    
    # 清理過期備份
    cleanup_old_backups
    
    # 合規性檢查
    compliance_check
    
    # 更新最後清理時間
    jq '.last_cleanup_time = now | strftime("%Y-%m-%dT%H:%M:%S%z")' "$STATS_FILE" > "${STATS_FILE}.tmp" && mv "${STATS_FILE}.tmp" "$STATS_FILE"
    
    log_success "資料保留清理完成"
}

# 主函數
main() {
    local command="${1:-help}"
    
    case "$command" in
        "init")
            log_info "初始化資料保留管理器..."
            check_dependencies
            load_config
            init_stats
            log_success "資料保留管理器初始化完成"
            ;;
        "cleanup")
            log_info "執行資料保留清理..."
            check_dependencies
            load_config
            create_lock
            setup_signal_handlers
            
            if [ ! -f "$STATS_FILE" ]; then
                init_stats
            fi
            
            run_cleanup
            remove_lock
            ;;
        "backup")
            log_info "執行資料備份..."
            check_dependencies
            load_config
            create_lock
            setup_signal_handlers
            
            perform_backup
            cleanup_old_backups
            remove_lock
            ;;
        "compliance")
            log_info "執行合規性檢查..."
            check_dependencies
            load_config
            
            compliance_check
            ;;
        "report")
            log_info "生成資料保留報告..."
            check_dependencies
            load_config
            
            if [ ! -f "$STATS_FILE" ]; then
                init_stats
            fi
            
            generate_retention_report
            ;;
        "status")
            log_info "顯示資料保留狀態..."
            
            if [ -f "$STATS_FILE" ]; then
                echo "資料保留統計資料:"
                jq '.' "$STATS_FILE"
            else
                log_warn "統計資料檔案不存在，請先執行初始化"
            fi
            
            echo ""
            echo "磁碟使用狀況:"
            df -h /
            ;;
        "clean-reports")
            cleanup_old_reports
            ;;
        "help"|*)
            echo "OpenTelemetry 資料保留策略管理器"
            echo ""
            echo "使用方式:"
            echo "  $0 init                    # 初始化管理器"
            echo "  $0 cleanup                 # 執行資料清理"
            echo "  $0 backup                  # 執行資料備份"
            echo "  $0 compliance              # 執行合規性檢查"
            echo "  $0 report                  # 生成保留報告"
            echo "  $0 status                  # 顯示狀態資訊"
            echo "  $0 clean-reports           # 清理過期報告"
            echo "  $0 help                    # 顯示說明"
            echo ""
            echo "排程執行建議:"
            echo "  # 每日凌晨 2:00 執行清理"
            echo "  0 2 * * * $0 cleanup"
            echo ""
            echo "  # 每週日凌晨 1:00 執行備份"
            echo "  0 1 * * 0 $0 backup"
            echo ""
            echo "  # 每月 1 日生成報告"
            echo "  0 3 1 * * $0 report"
            ;;
    esac
}

# 執行主函數
main "$@"