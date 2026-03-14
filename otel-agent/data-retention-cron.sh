#!/bin/bash

# OpenTelemetry 資料保留策略 Cron 任務配置腳本
# 用於設定自動執行的資料清理和備份任務

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RETENTION_MANAGER="${SCRIPT_DIR}/data-retention-manager.sh"
CRON_FILE="/tmp/otel-retention-cron"

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

# 檢查資料保留管理器是否存在
check_retention_manager() {
    if [ ! -f "$RETENTION_MANAGER" ]; then
        log_error "資料保留管理器不存在: $RETENTION_MANAGER"
        exit 1
    fi
    
    if [ ! -x "$RETENTION_MANAGER" ]; then
        log_error "資料保留管理器沒有執行權限: $RETENTION_MANAGER"
        exit 1
    fi
    
    log_success "資料保留管理器檢查通過"
}

# 創建 cron 任務配置
create_cron_config() {
    log_info "創建 cron 任務配置..."
    
    cat > "$CRON_FILE" << EOF
# OpenTelemetry 資料保留策略自動任務
# 由 data-retention-cron.sh 自動生成

# 每日凌晨 2:00 執行資料清理
0 2 * * * $RETENTION_MANAGER cleanup >> ${SCRIPT_DIR}/cron-cleanup.log 2>&1

# 每週日凌晨 1:00 執行資料備份
0 1 * * 0 $RETENTION_MANAGER backup >> ${SCRIPT_DIR}/cron-backup.log 2>&1

# 每月 1 日凌晨 3:00 生成保留報告
0 3 1 * * $RETENTION_MANAGER report >> ${SCRIPT_DIR}/cron-report.log 2>&1

# 每週三凌晨 4:00 執行合規性檢查
0 4 * * 3 $RETENTION_MANAGER compliance >> ${SCRIPT_DIR}/cron-compliance.log 2>&1

# 每月 15 日凌晨 5:00 清理過期報告
0 5 15 * * $RETENTION_MANAGER clean-reports >> ${SCRIPT_DIR}/cron-cleanup-reports.log 2>&1

# 每小時檢查磁碟使用率（僅記錄狀態）
0 * * * * $RETENTION_MANAGER status >> ${SCRIPT_DIR}/cron-status.log 2>&1

EOF
    
    log_success "cron 任務配置已創建: $CRON_FILE"
}

# 安裝 cron 任務
install_cron_jobs() {
    log_info "安裝 cron 任務..."
    
    # 備份現有的 crontab
    if crontab -l > /dev/null 2>&1; then
        crontab -l > "${SCRIPT_DIR}/crontab-backup-$(date +%Y%m%d-%H%M%S).txt"
        log_info "已備份現有的 crontab"
    fi
    
    # 移除舊的 OpenTelemetry 資料保留任務
    if crontab -l 2>/dev/null | grep -v "OpenTelemetry 資料保留" | grep -v "$RETENTION_MANAGER" > "${CRON_FILE}.tmp"; then
        mv "${CRON_FILE}.tmp" "${CRON_FILE}.clean"
    else
        touch "${CRON_FILE}.clean"
    fi
    
    # 合併新的任務
    cat "${CRON_FILE}.clean" "$CRON_FILE" > "${CRON_FILE}.final"
    
    # 安裝新的 crontab
    if crontab "${CRON_FILE}.final"; then
        log_success "cron 任務安裝成功"
    else
        log_error "cron 任務安裝失敗"
        exit 1
    fi
    
    # 清理臨時檔案
    rm -f "${CRON_FILE}.tmp" "${CRON_FILE}.clean" "${CRON_FILE}.final"
}

# 移除 cron 任務
remove_cron_jobs() {
    log_info "移除 OpenTelemetry 資料保留 cron 任務..."
    
    if crontab -l 2>/dev/null | grep -v "OpenTelemetry 資料保留" | grep -v "$RETENTION_MANAGER" > "${CRON_FILE}.clean"; then
        if crontab "${CRON_FILE}.clean"; then
            log_success "cron 任務移除成功"
        else
            log_error "cron 任務移除失敗"
            exit 1
        fi
        rm -f "${CRON_FILE}.clean"
    else
        log_info "沒有找到相關的 cron 任務"
    fi
}

# 顯示當前的 cron 任務
show_cron_jobs() {
    log_info "當前的 cron 任務:"
    echo ""
    
    if crontab -l 2>/dev/null; then
        echo ""
        log_info "OpenTelemetry 資料保留相關任務:"
        crontab -l 2>/dev/null | grep -E "(OpenTelemetry|$RETENTION_MANAGER)" || log_info "沒有找到相關任務"
    else
        log_warn "沒有設定任何 cron 任務"
    fi
}

# 測試 cron 任務
test_cron_jobs() {
    log_info "測試資料保留管理器功能..."
    
    # 測試初始化
    if "$RETENTION_MANAGER" init; then
        log_success "初始化測試通過"
    else
        log_error "初始化測試失敗"
        return 1
    fi
    
    # 測試狀態檢查
    if "$RETENTION_MANAGER" status; then
        log_success "狀態檢查測試通過"
    else
        log_error "狀態檢查測試失敗"
        return 1
    fi
    
    # 測試合規性檢查
    if "$RETENTION_MANAGER" compliance; then
        log_success "合規性檢查測試通過"
    else
        log_error "合規性檢查測試失敗"
        return 1
    fi
    
    log_success "所有測試通過"
}

# 創建日誌輪轉配置
create_logrotate_config() {
    local logrotate_file="/tmp/otel-retention-logrotate"
    
    log_info "創建日誌輪轉配置..."
    
    cat > "$logrotate_file" << EOF
# OpenTelemetry 資料保留日誌輪轉配置
${SCRIPT_DIR}/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 644 $(whoami) $(id -gn)
    postrotate
        # 重新載入日誌服務（如果需要）
    endscript
}
EOF
    
    log_success "日誌輪轉配置已創建: $logrotate_file"
    log_info "請將此配置複製到 /etc/logrotate.d/ 目錄（需要 root 權限）"
    echo "sudo cp $logrotate_file /etc/logrotate.d/otel-retention"
}

# 創建監控腳本
create_monitoring_script() {
    local monitor_script="${SCRIPT_DIR}/retention-monitor.sh"
    
    log_info "創建監控腳本..."
    
    cat > "$monitor_script" << 'EOF'
#!/bin/bash

# OpenTelemetry 資料保留監控腳本
# 監控清理任務的執行狀況和系統健康狀態

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATS_FILE="${SCRIPT_DIR}/retention-stats.json"
ALERT_LOG="${SCRIPT_DIR}/retention-alerts.log"

# 檢查清理任務是否正常執行
check_cleanup_health() {
    if [ ! -f "$STATS_FILE" ]; then
        echo "$(date): 警告 - 統計資料檔案不存在" >> "$ALERT_LOG"
        return 1
    fi
    
    local last_cleanup
    last_cleanup=$(jq -r '.last_cleanup_time' "$STATS_FILE" 2>/dev/null || echo "null")
    
    if [ "$last_cleanup" = "null" ]; then
        echo "$(date): 警告 - 從未執行過清理任務" >> "$ALERT_LOG"
        return 1
    fi
    
    # 檢查最後清理時間是否超過 25 小時（應該每日執行）
    local last_cleanup_timestamp
    last_cleanup_timestamp=$(date -d "$last_cleanup" +%s 2>/dev/null || echo "0")
    
    local current_timestamp
    current_timestamp=$(date +%s)
    
    local hours_since_cleanup
    hours_since_cleanup=$(( (current_timestamp - last_cleanup_timestamp) / 3600 ))
    
    if [ "$hours_since_cleanup" -gt 25 ]; then
        echo "$(date): 警告 - 清理任務已超過 $hours_since_cleanup 小時未執行" >> "$ALERT_LOG"
        return 1
    fi
    
    return 0
}

# 檢查磁碟使用率
check_disk_usage() {
    local usage
    usage=$(df / | awk 'NR==2 {print $5}' | sed 's/%//')
    
    if [ "$usage" -gt 85 ]; then
        echo "$(date): 警告 - 磁碟使用率過高: $usage%" >> "$ALERT_LOG"
        return 1
    fi
    
    return 0
}

# 檢查錯誤數量
check_error_count() {
    if [ ! -f "$STATS_FILE" ]; then
        return 1
    fi
    
    local error_count
    error_count=$(jq -r '.errors' "$STATS_FILE" 2>/dev/null || echo "0")
    
    if [ "$error_count" -gt 5 ]; then
        echo "$(date): 警告 - 錯誤數量過多: $error_count" >> "$ALERT_LOG"
        return 1
    fi
    
    return 0
}

# 主監控函數
main() {
    local alerts=0
    
    if ! check_cleanup_health; then
        alerts=$((alerts + 1))
    fi
    
    if ! check_disk_usage; then
        alerts=$((alerts + 1))
    fi
    
    if ! check_error_count; then
        alerts=$((alerts + 1))
    fi
    
    if [ "$alerts" -eq 0 ]; then
        echo "$(date): 資訊 - 所有檢查通過" >> "$ALERT_LOG"
    else
        echo "$(date): 警告 - 發現 $alerts 個問題" >> "$ALERT_LOG"
    fi
    
    # 清理過期的告警日誌（保留 30 天）
    find "$(dirname "$ALERT_LOG")" -name "retention-alerts.log" -mtime +30 -delete 2>/dev/null || true
    
    exit $alerts
}

main "$@"
EOF
    
    chmod +x "$monitor_script"
    log_success "監控腳本已創建: $monitor_script"
}

# 主函數
main() {
    local command="${1:-help}"
    
    case "$command" in
        "install")
            log_info "安裝 OpenTelemetry 資料保留 cron 任務..."
            check_retention_manager
            create_cron_config
            install_cron_jobs
            create_logrotate_config
            create_monitoring_script
            log_success "安裝完成"
            ;;
        "remove")
            log_info "移除 OpenTelemetry 資料保留 cron 任務..."
            remove_cron_jobs
            log_success "移除完成"
            ;;
        "show")
            show_cron_jobs
            ;;
        "test")
            log_info "測試資料保留管理器..."
            check_retention_manager
            test_cron_jobs
            ;;
        "monitor")
            log_info "創建監控腳本..."
            create_monitoring_script
            ;;
        "logrotate")
            create_logrotate_config
            ;;
        "help"|*)
            echo "OpenTelemetry 資料保留策略 Cron 任務管理器"
            echo ""
            echo "使用方式:"
            echo "  $0 install     # 安裝 cron 任務"
            echo "  $0 remove      # 移除 cron 任務"
            echo "  $0 show        # 顯示當前 cron 任務"
            echo "  $0 test        # 測試資料保留管理器"
            echo "  $0 monitor     # 創建監控腳本"
            echo "  $0 logrotate   # 創建日誌輪轉配置"
            echo "  $0 help        # 顯示說明"
            echo ""
            echo "安裝後的自動任務:"
            echo "  - 每日凌晨 2:00 執行資料清理"
            echo "  - 每週日凌晨 1:00 執行資料備份"
            echo "  - 每月 1 日凌晨 3:00 生成保留報告"
            echo "  - 每週三凌晨 4:00 執行合規性檢查"
            echo "  - 每月 15 日凌晨 5:00 清理過期報告"
            echo "  - 每小時檢查系統狀態"
            ;;
    esac
}

# 執行主函數
main "$@"