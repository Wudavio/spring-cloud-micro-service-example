#!/bin/bash

# OpenTelemetry 配置熱重載腳本
# 支援運行時配置變更和配置熱重載機制

set -e

# 腳本目錄
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 顏色輸出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日誌函數
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" >&2
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

# 配置檔案監控和熱重載
CONFIG_WATCH_PID=""
CONFIG_FILE=""
RELOAD_CALLBACK=""

# 顯示使用說明
show_help() {
    cat << EOF
OpenTelemetry 配置熱重載工具

使用方式:
    $0 [選項] <命令> [參數]

命令:
    watch <配置檔案> [回調腳本]    開始監控配置檔案變更
    stop                          停止配置檔案監控
    reload <配置檔案>             手動重載配置
    update <鍵> <值> <配置檔案>   更新單個配置項
    status                        顯示監控狀態
    test-reload                   測試重載功能

選項:
    -h, --help                    顯示此說明
    -v, --verbose                 詳細輸出
    --interval <秒數>             監控間隔（預設 5 秒）

範例:
    $0 watch /app/otel-config.env
    $0 update OTEL_TRACES_SAMPLER_ARG 0.5 /app/otel-config.env
    $0 reload /app/otel-config.env
    $0 stop

EOF
}

# 檢查配置檔案變更
check_config_changes() {
    local config_file="$1"
    local last_modified_file="/tmp/otel-config-lastmod"
    
    if [[ ! -f "$config_file" ]]; then
        log_error "配置檔案不存在: $config_file"
        return 1
    fi
    
    # 獲取檔案修改時間
    local current_mtime
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        current_mtime=$(stat -f %m "$config_file")
    else
        # Linux
        current_mtime=$(stat -c %Y "$config_file")
    fi
    
    # 檢查是否有變更
    if [[ -f "$last_modified_file" ]]; then
        local last_mtime=$(cat "$last_modified_file")
        if [[ "$current_mtime" != "$last_mtime" ]]; then
            echo "$current_mtime" > "$last_modified_file"
            return 0  # 有變更
        else
            return 1  # 無變更
        fi
    else
        echo "$current_mtime" > "$last_modified_file"
        return 1  # 首次檢查
    fi
}

# 重載配置
reload_config() {
    local config_file="$1"
    local callback_script="$2"
    
    log_info "重載配置檔案: $config_file"
    
    # 驗證配置檔案
    if ! "$SCRIPT_DIR/config-validator.sh" validate "$config_file"; then
        log_error "配置檔案驗證失敗，跳過重載"
        return 1
    fi
    
    # 載入新配置
    if ! source "$SCRIPT_DIR/config-fallback.sh" && load_config_with_fallback "$config_file"; then
        log_error "配置載入失敗"
        return 1
    fi
    
    # 執行回調腳本
    if [[ -n "$callback_script" && -x "$callback_script" ]]; then
        log_info "執行重載回調: $callback_script"
        if "$callback_script" "$config_file"; then
            log_success "回調執行成功"
        else
            log_warning "回調執行失敗"
        fi
    fi
    
    # 通知相關服務
    notify_services_config_change
    
    log_success "配置重載完成"
    return 0
}

# 通知服務配置變更
notify_services_config_change() {
    log_info "通知服務配置變更..."
    
    # 發送 SIGHUP 信號給 Java 進程（如果支援）
    local java_pids=$(pgrep -f "java.*opentelemetry-javaagent" || true)
    if [[ -n "$java_pids" ]]; then
        for pid in $java_pids; do
            log_info "通知 Java 進程 $pid 配置變更"
            kill -HUP "$pid" 2>/dev/null || log_warning "無法通知進程 $pid"
        done
    fi
    
    # 更新環境變數檔案時間戳
    touch "/tmp/otel-config-updated-$(date +%s)"
}

# 監控配置檔案
watch_config() {
    local config_file="$1"
    local callback_script="$2"
    local interval="${3:-5}"
    
    if [[ ! -f "$config_file" ]]; then
        log_error "配置檔案不存在: $config_file"
        return 1
    fi
    
    CONFIG_FILE="$config_file"
    RELOAD_CALLBACK="$callback_script"
    
    log_info "開始監控配置檔案: $config_file"
    log_info "監控間隔: $interval 秒"
    
    # 清理舊的監控狀態
    rm -f "/tmp/otel-config-lastmod"
    
    # 監控循環
    while true; do
        if check_config_changes "$config_file"; then
            log_info "檢測到配置檔案變更"
            reload_config "$config_file" "$callback_script"
        fi
        sleep "$interval"
    done
}

# 停止監控
stop_watch() {
    if [[ -n "$CONFIG_WATCH_PID" ]]; then
        log_info "停止配置監控 (PID: $CONFIG_WATCH_PID)"
        kill "$CONFIG_WATCH_PID" 2>/dev/null || true
        CONFIG_WATCH_PID=""
    fi
    
    # 清理臨時檔案
    rm -f "/tmp/otel-config-lastmod"
    rm -f "/tmp/otel-config-watch.pid"
    
    log_success "配置監控已停止"
}

# 更新單個配置項
update_config_value() {
    local key="$1"
    local value="$2"
    local config_file="$3"
    
    if [[ -z "$key" || -z "$value" || -z "$config_file" ]]; then
        log_error "用法: update_config_value <鍵> <值> <配置檔案>"
        return 1
    fi
    
    if [[ ! -f "$config_file" ]]; then
        log_error "配置檔案不存在: $config_file"
        return 1
    fi
    
    log_info "更新配置項: $key=$value"
    
    # 驗證新值
    if ! "$SCRIPT_DIR/config-validator.sh" check-value "$key" "$value"; then
        log_error "配置值無效: $key=$value"
        return 1
    fi
    
    # 備份原始檔案
    cp "$config_file" "$config_file.backup.$(date +%s)"
    
    # 更新配置項
    if grep -q "^$key=" "$config_file"; then
        # 更新現有配置項
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            sed -i '' "s/^$key=.*/$key=$value/" "$config_file"
        else
            # Linux
            sed -i "s/^$key=.*/$key=$value/" "$config_file"
        fi
        log_info "已更新現有配置項: $key"
    else
        # 添加新配置項
        echo "$key=$value" >> "$config_file"
        log_info "已添加新配置項: $key"
    fi
    
    # 驗證更新後的配置檔案
    if "$SCRIPT_DIR/config-validator.sh" validate "$config_file"; then
        log_success "配置更新成功: $key=$value"
        
        # 觸發重載
        reload_config "$config_file"
    else
        log_error "配置更新後驗證失敗，恢復備份"
        mv "$config_file.backup.$(date +%s)" "$config_file"
        return 1
    fi
}

# 顯示監控狀態
show_status() {
    log_info "配置熱重載狀態:"
    
    if [[ -f "/tmp/otel-config-watch.pid" ]]; then
        local watch_pid=$(cat "/tmp/otel-config-watch.pid")
        if kill -0 "$watch_pid" 2>/dev/null; then
            echo "  狀態: 運行中 (PID: $watch_pid)"
            if [[ -n "$CONFIG_FILE" ]]; then
                echo "  監控檔案: $CONFIG_FILE"
            fi
        else
            echo "  狀態: 已停止 (PID 檔案過期)"
            rm -f "/tmp/otel-config-watch.pid"
        fi
    else
        echo "  狀態: 未運行"
    fi
    
    # 顯示最近的配置更新
    local update_files=$(ls -t /tmp/otel-config-updated-* 2>/dev/null | head -3 || true)
    if [[ -n "$update_files" ]]; then
        echo "  最近更新:"
        for file in $update_files; do
            local timestamp=$(basename "$file" | sed 's/otel-config-updated-//')
            local update_time=$(date -r "$timestamp" 2>/dev/null || echo "未知時間")
            echo "    $update_time"
        done
    fi
}

# 測試重載功能
test_reload() {
    log_info "測試配置重載功能..."
    
    # 創建測試配置檔案
    local test_config="/tmp/test-otel-config.env"
    cat > "$test_config" << EOF
# 測試配置檔案
OTEL_SERVICE_NAME=test-service
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
OTEL_TRACES_SAMPLER_ARG=0.1
OTEL_LOG_LEVEL=INFO
EOF
    
    log_info "創建測試配置檔案: $test_config"
    
    # 測試配置驗證
    if "$SCRIPT_DIR/config-validator.sh" validate "$test_config"; then
        log_success "配置驗證測試通過"
    else
        log_error "配置驗證測試失敗"
        return 1
    fi
    
    # 測試配置載入
    if reload_config "$test_config"; then
        log_success "配置重載測試通過"
    else
        log_error "配置重載測試失敗"
        return 1
    fi
    
    # 測試配置更新
    if update_config_value "OTEL_TRACES_SAMPLER_ARG" "0.5" "$test_config"; then
        log_success "配置更新測試通過"
    else
        log_error "配置更新測試失敗"
        return 1
    fi
    
    # 清理測試檔案
    rm -f "$test_config" "$test_config.backup."*
    
    log_success "所有測試通過！"
}

# 信號處理
cleanup() {
    log_info "收到終止信號，清理資源..."
    stop_watch
    exit 0
}

trap cleanup SIGTERM SIGINT

# 主函數
main() {
    local verbose=false
    local interval=5
    
    # 解析命令列參數
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verbose)
                verbose=true
                shift
                ;;
            --interval)
                interval="$2"
                shift 2
                ;;
            watch)
                if [[ $# -lt 2 ]]; then
                    log_error "watch 命令需要配置檔案參數"
                    exit 1
                fi
                
                # 在背景執行監控
                watch_config "$2" "$3" "$interval" &
                CONFIG_WATCH_PID=$!
                echo "$CONFIG_WATCH_PID" > "/tmp/otel-config-watch.pid"
                
                log_success "配置監控已啟動 (PID: $CONFIG_WATCH_PID)"
                wait "$CONFIG_WATCH_PID"
                exit $?
                ;;
            stop)
                stop_watch
                exit 0
                ;;
            reload)
                if [[ $# -lt 2 ]]; then
                    log_error "reload 命令需要配置檔案參數"
                    exit 1
                fi
                reload_config "$2" "$3"
                exit $?
                ;;
            update)
                if [[ $# -lt 4 ]]; then
                    log_error "update 命令需要 <鍵> <值> <配置檔案> 參數"
                    exit 1
                fi
                update_config_value "$2" "$3" "$4"
                exit $?
                ;;
            status)
                show_status
                exit 0
                ;;
            test-reload)
                test_reload
                exit $?
                ;;
            *)
                log_error "未知的命令或選項: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 如果沒有提供命令，顯示說明
    show_help
}

# 執行主函數
main "$@"