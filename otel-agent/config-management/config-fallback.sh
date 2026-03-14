#!/bin/bash

# OpenTelemetry 配置容錯處理腳本
# 提供配置載入時的容錯機制和預設值回退

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

# 容錯配置載入器
# 使用方式: source config-fallback.sh && load_config_with_fallback <config_file>
load_config_with_fallback() {
    local config_file="$1"
    local fallback_applied=false
    
    # 檢查配置檔案是否存在
    if [[ ! -f "$config_file" ]]; then
        log_error "配置檔案不存在: $config_file"
        log_info "使用預設配置"
        load_default_config
        return 1
    fi
    
    log_info "載入配置檔案: $config_file"
    
    # 載入配置檔案
    while IFS='=' read -r key value; do
        # 跳過註解和空行
        if [[ "$key" =~ ^[[:space:]]*# ]] || [[ -z "$key" ]]; then
            continue
        fi
        
        # 移除值的引號和空白
        value=$(echo "$value" | sed 's/^["'\'']\|["'\'']$//g' | xargs)
        
        # 如果值為空，嘗試使用預設值
        if [[ -z "$value" ]]; then
            local default_value
            if default_value=$(get_fallback_value "$key"); then
                value="$default_value"
                log_warning "配置項 $key 為空，使用預設值: $value"
                fallback_applied=true
            else
                log_warning "配置項 $key 為空且沒有預設值"
                continue
            fi
        fi
        
        # 驗證配置值
        if ! validate_and_fix_value "$key" "$value"; then
            local fixed_value
            if fixed_value=$(get_fallback_value "$key"); then
                value="$fixed_value"
                log_warning "配置項 $key 值無效，使用預設值: $value"
                fallback_applied=true
            else
                log_error "配置項 $key 值無效且沒有預設值: $value"
                continue
            fi
        fi
        
        # 設定環境變數
        export "$key"="$value"
        
    done < <(grep -E '^[A-Z_]+=.*' "$config_file" || true)
    
    # 檢查並設定必要的配置項
    ensure_required_configs
    
    if [[ "$fallback_applied" == "true" ]]; then
        log_warning "配置載入完成，但應用了容錯處理"
        return 2
    else
        log_success "配置載入完成"
        return 0
    fi
}

# 載入預設配置
load_default_config() {
    log_info "載入預設 OpenTelemetry 配置"
    
    # 基本配置
    export OTEL_SERVICE_VERSION="${OTEL_SERVICE_VERSION:-1.0.0}"
    export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"
    export OTEL_TRACES_SAMPLER="${OTEL_TRACES_SAMPLER:-traceidratio}"
    export OTEL_TRACES_SAMPLER_ARG="${OTEL_TRACES_SAMPLER_ARG:-0.1}"
    export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-otlp}"
    export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
    export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-otlp}"
    
    # 時間間隔配置
    export OTEL_METRIC_EXPORT_INTERVAL="${OTEL_METRIC_EXPORT_INTERVAL:-30000}"
    export OTEL_BSP_MAX_EXPORT_BATCH_SIZE="${OTEL_BSP_MAX_EXPORT_BATCH_SIZE:-512}"
    export OTEL_BSP_EXPORT_TIMEOUT="${OTEL_BSP_EXPORT_TIMEOUT:-30000}"
    export OTEL_BSP_SCHEDULE_DELAY="${OTEL_BSP_SCHEDULE_DELAY:-5000}"
    export OTEL_BSP_MAX_QUEUE_SIZE="${OTEL_BSP_MAX_QUEUE_SIZE:-2048}"
    
    # 除錯和日誌配置
    export OTEL_JAVAAGENT_DEBUG="${OTEL_JAVAAGENT_DEBUG:-false}"
    export OTEL_LOG_LEVEL="${OTEL_LOG_LEVEL:-INFO}"
    
    # 壓縮和儀表化配置
    export OTEL_EXPORTER_OTLP_COMPRESSION="${OTEL_EXPORTER_OTLP_COMPRESSION:-gzip}"
    export OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED="${OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED:-true}"
    
    # Java Agent 配置
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--javaagent:/app/opentelemetry-javaagent.jar}"
    export OTEL_JAVAAGENT_CONFIGURATION_FILE="${OTEL_JAVAAGENT_CONFIGURATION_FILE:-/app/otel-config.properties}"
    
    # 預設端點 (如果沒有設定)
    if [[ -z "$OTEL_EXPORTER_OTLP_ENDPOINT" ]]; then
        export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
        log_warning "未設定 OTLP 端點，使用預設值: $OTEL_EXPORTER_OTLP_ENDPOINT"
    fi
    
    # 預設服務名稱 (如果沒有設定)
    if [[ -z "$OTEL_SERVICE_NAME" ]]; then
        export OTEL_SERVICE_NAME="unknown-service"
        log_warning "未設定服務名稱，使用預設值: $OTEL_SERVICE_NAME"
    fi
}

# 獲取容錯值
get_fallback_value() {
    local key="$1"
    
    case "$key" in
        "OTEL_SERVICE_VERSION")
            echo "1.0.0"
            ;;
        "OTEL_EXPORTER_OTLP_PROTOCOL")
            echo "grpc"
            ;;
        "OTEL_TRACES_SAMPLER")
            echo "traceidratio"
            ;;
        "OTEL_TRACES_SAMPLER_ARG")
            echo "0.1"
            ;;
        "OTEL_METRICS_EXPORTER"|"OTEL_TRACES_EXPORTER"|"OTEL_LOGS_EXPORTER")
            echo "otlp"
            ;;
        "OTEL_METRIC_EXPORT_INTERVAL")
            echo "30000"
            ;;
        "OTEL_JAVAAGENT_DEBUG")
            echo "false"
            ;;
        "OTEL_LOG_LEVEL")
            echo "INFO"
            ;;
        "OTEL_BSP_MAX_EXPORT_BATCH_SIZE")
            echo "512"
            ;;
        "OTEL_BSP_EXPORT_TIMEOUT")
            echo "30000"
            ;;
        "OTEL_BSP_SCHEDULE_DELAY")
            echo "5000"
            ;;
        "OTEL_BSP_MAX_QUEUE_SIZE")
            echo "2048"
            ;;
        "OTEL_EXPORTER_OTLP_COMPRESSION")
            echo "gzip"
            ;;
        "OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED")
            echo "true"
            ;;
        "JAVA_TOOL_OPTIONS")
            echo "-javaagent:/app/opentelemetry-javaagent.jar"
            ;;
        "OTEL_JAVAAGENT_CONFIGURATION_FILE")
            echo "/app/otel-config.properties"
            ;;
        "OTEL_EXPORTER_OTLP_ENDPOINT")
            echo "http://localhost:4317"
            ;;
        "OTEL_SERVICE_NAME")
            echo "unknown-service"
            ;;
        *)
            return 1
            ;;
    esac
    return 0
}

# 驗證並修復配置值
validate_and_fix_value() {
    local key="$1"
    local value="$2"
    
    case "$key" in
        "OTEL_EXPORTER_OTLP_ENDPOINT")
            # 驗證 OTLP 端點格式
            if [[ ! "$value" =~ ^https?://[a-zA-Z0-9.-]+:[0-9]+$ ]]; then
                return 1
            fi
            ;;
        "OTEL_TRACES_SAMPLER_ARG")
            # 驗證取樣率
            if ! [[ "$value" =~ ^[0-9]*\.?[0-9]+$ ]] || (( $(echo "$value < 0" | bc -l 2>/dev/null || echo "1") )) || (( $(echo "$value > 1" | bc -l 2>/dev/null || echo "1") )); then
                return 1
            fi
            ;;
        "OTEL_METRIC_EXPORT_INTERVAL"|"OTEL_BSP_EXPORT_TIMEOUT"|"OTEL_BSP_SCHEDULE_DELAY")
            # 驗證時間間隔
            if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 1000 ]] || [[ "$value" -gt 300000 ]]; then
                return 1
            fi
            ;;
        "OTEL_BSP_MAX_EXPORT_BATCH_SIZE"|"OTEL_BSP_MAX_QUEUE_SIZE")
            # 驗證批次大小
            if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 1 ]] || [[ "$value" -gt 100000 ]]; then
                return 1
            fi
            ;;
        "OTEL_JAVAAGENT_DEBUG"|"OTEL_INSTRUMENTATION_"*"_ENABLED")
            # 驗證布林值
            local lower_value=$(echo "$value" | tr '[:upper:]' '[:lower:]')
            case "$lower_value" in
                "true"|"false")
                    return 0
                    ;;
                *)
                    return 1
                    ;;
            esac
            ;;
        "OTEL_SERVICE_NAME")
            # 驗證服務名稱格式
            if [[ ! "$value" =~ ^[a-z0-9-]+$ ]] || [[ ${#value} -lt 2 ]] || [[ ${#value} -gt 50 ]]; then
                return 1
            fi
            ;;
        "OTEL_LOG_LEVEL")
            # 驗證日誌級別
            local upper_value=$(echo "$value" | tr '[:lower:]' '[:upper:]')
            case "$upper_value" in
                "TRACE"|"DEBUG"|"INFO"|"WARN"|"ERROR"|"OFF")
                    return 0
                    ;;
                *)
                    return 1
                    ;;
            esac
            ;;
        "OTEL_EXPORTER_OTLP_PROTOCOL")
            # 驗證協議類型
            local lower_value=$(echo "$value" | tr '[:upper:]' '[:lower:]')
            case "$lower_value" in
                "grpc"|"http/protobuf")
                    return 0
                    ;;
                *)
                    return 1
                    ;;
            esac
            ;;
    esac
    
    return 0
}

# 確保必要配置項存在
ensure_required_configs() {
    local required_configs=(
        "OTEL_SERVICE_NAME"
        "OTEL_EXPORTER_OTLP_ENDPOINT"
    )
    
    for config in "${required_configs[@]}"; do
        if [[ -z "${!config}" ]]; then
            local default_value
            if default_value=$(get_fallback_value "$config"); then
                export "$config"="$default_value"
                log_warning "設定必要配置項 $config 的預設值: $default_value"
            else
                log_error "缺少必要配置項 $config 且沒有預設值"
            fi
        fi
    done
}

# 環境特定的配置調整
adjust_for_environment() {
    local environment="${1:-${DEPLOYMENT_ENVIRONMENT:-development}}"
    
    case "$environment" in
        "development")
            # 開發環境：高取樣率，詳細日誌
            export OTEL_TRACES_SAMPLER_ARG="${OTEL_TRACES_SAMPLER_ARG:-1.0}"
            export OTEL_JAVAAGENT_DEBUG="${OTEL_JAVAAGENT_DEBUG:-true}"
            export OTEL_LOG_LEVEL="${OTEL_LOG_LEVEL:-DEBUG}"
            export OTEL_METRIC_EXPORT_INTERVAL="${OTEL_METRIC_EXPORT_INTERVAL:-10000}"
            ;;
        "testing")
            # 測試環境：中等取樣率
            export OTEL_TRACES_SAMPLER_ARG="${OTEL_TRACES_SAMPLER_ARG:-0.5}"
            export OTEL_JAVAAGENT_DEBUG="${OTEL_JAVAAGENT_DEBUG:-false}"
            export OTEL_LOG_LEVEL="${OTEL_LOG_LEVEL:-INFO}"
            export OTEL_METRIC_EXPORT_INTERVAL="${OTEL_METRIC_EXPORT_INTERVAL:-15000}"
            ;;
        "staging")
            # 預發布環境：低取樣率
            export OTEL_TRACES_SAMPLER_ARG="${OTEL_TRACES_SAMPLER_ARG:-0.1}"
            export OTEL_JAVAAGENT_DEBUG="${OTEL_JAVAAGENT_DEBUG:-false}"
            export OTEL_LOG_LEVEL="${OTEL_LOG_LEVEL:-WARN}"
            export OTEL_METRIC_EXPORT_INTERVAL="${OTEL_METRIC_EXPORT_INTERVAL:-30000}"
            ;;
        "production")
            # 生產環境：最低取樣率，最少日誌
            export OTEL_TRACES_SAMPLER_ARG="${OTEL_TRACES_SAMPLER_ARG:-0.01}"
            export OTEL_JAVAAGENT_DEBUG="${OTEL_JAVAAGENT_DEBUG:-false}"
            export OTEL_LOG_LEVEL="${OTEL_LOG_LEVEL:-ERROR}"
            export OTEL_METRIC_EXPORT_INTERVAL="${OTEL_METRIC_EXPORT_INTERVAL:-60000}"
            ;;
    esac
    
    log_info "已調整配置以適應 $environment 環境"
}

# 健康檢查配置
health_check_config() {
    local issues=0
    
    log_info "執行配置健康檢查..."
    
    # 檢查必要配置項
    if [[ -z "$OTEL_SERVICE_NAME" ]]; then
        log_error "缺少服務名稱配置"
        ((issues++))
    fi
    
    if [[ -z "$OTEL_EXPORTER_OTLP_ENDPOINT" ]]; then
        log_error "缺少 OTLP 端點配置"
        ((issues++))
    fi
    
    # 檢查端點連通性 (可選)
    if [[ -n "$OTEL_EXPORTER_OTLP_ENDPOINT" ]]; then
        local host_port=$(echo "$OTEL_EXPORTER_OTLP_ENDPOINT" | sed 's|^https\?://||')
        if command -v nc >/dev/null 2>&1; then
            if ! nc -z ${host_port/:/ } 2>/dev/null; then
                log_warning "無法連接到 OTLP 端點: $OTEL_EXPORTER_OTLP_ENDPOINT"
            fi
        fi
    fi
    
    # 檢查 Java Agent 檔案
    if [[ -n "$JAVA_TOOL_OPTIONS" ]] && [[ "$JAVA_TOOL_OPTIONS" =~ -javaagent:([^[:space:]]+) ]]; then
        local agent_path="${BASH_REMATCH[1]}"
        if [[ ! -f "$agent_path" ]]; then
            log_warning "OpenTelemetry Java Agent 檔案不存在: $agent_path"
        fi
    fi
    
    if [[ "$issues" -eq 0 ]]; then
        log_success "配置健康檢查通過"
        return 0
    else
        log_error "配置健康檢查發現 $issues 個問題"
        return 1
    fi
}

# 顯示當前配置
show_current_config() {
    log_info "當前 OpenTelemetry 配置:"
    
    local otel_vars=($(env | grep '^OTEL_' | cut -d'=' -f1 | sort))
    
    for var in "${otel_vars[@]}"; do
        echo "  $var=${!var}"
    done
    
    if [[ -n "$JAVA_TOOL_OPTIONS" ]]; then
        echo "  JAVA_TOOL_OPTIONS=$JAVA_TOOL_OPTIONS"
    fi
}

# 如果腳本被直接執行
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-}" in
        "load")
            if [[ $# -lt 2 ]]; then
                log_error "用法: $0 load <配置檔案>"
                exit 1
            fi
            load_config_with_fallback "$2"
            ;;
        "default")
            load_default_config
            ;;
        "health-check")
            health_check_config
            ;;
        "show")
            show_current_config
            ;;
        "adjust")
            adjust_for_environment "$2"
            ;;
        *)
            echo "用法: $0 {load|default|health-check|show|adjust} [參數]"
            echo ""
            echo "命令:"
            echo "  load <檔案>     載入配置檔案並應用容錯處理"
            echo "  default         載入預設配置"
            echo "  health-check    檢查配置健康狀況"
            echo "  show            顯示當前配置"
            echo "  adjust <環境>   調整配置以適應指定環境"
            echo ""
            echo "或者在其他腳本中使用:"
            echo "  source $0"
            echo "  load_config_with_fallback <配置檔案>"
            exit 1
            ;;
    esac
fi