#!/bin/bash

# OpenTelemetry 配置驗證器
# 提供配置值驗證邏輯、預設值和錯誤處理

set -e

# 顏色輸出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日誌函數
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 獲取預設配置值的函數（相容舊版 bash）
get_default_config_value() {
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
        *)
            return 1
            ;;
    esac
    return 0
}

# 必要配置項
REQUIRED_CONFIGS=(
    "OTEL_SERVICE_NAME"
    "OTEL_EXPORTER_OTLP_ENDPOINT"
)

# 顯示使用說明
show_help() {
    cat << EOF
OpenTelemetry 配置驗證器

使用方式:
    $0 [選項] <命令> [參數]

命令:
    validate <配置檔案>           驗證配置檔案並應用容錯處理
    fix <配置檔案>               修復配置檔案中的問題
    check-value <鍵> <值>        檢查單個配置值的有效性
    get-default <鍵>             獲取配置項的預設值
    list-defaults                列出所有預設值

選項:
    -h, --help                   顯示此說明
    -v, --verbose                詳細輸出
    --strict                     嚴格模式，不允許使用預設值
    --output <檔案>              輸出修復後的配置到指定檔案

範例:
    $0 validate config.env
    $0 fix config.env --output fixed-config.env
    $0 check-value OTEL_TRACES_SAMPLER_ARG 1.5
    $0 get-default OTEL_SERVICE_VERSION

EOF
}

# 驗證 OTLP 端點格式
validate_otlp_endpoint() {
    local endpoint="$1"
    
    if [[ -z "$endpoint" ]]; then
        return 1
    fi
    
    # 檢查基本 URL 格式
    if [[ ! "$endpoint" =~ ^https?://[a-zA-Z0-9.-]+:[0-9]+$ ]]; then
        return 1
    fi
    
    # 檢查埠號範圍
    local port=$(echo "$endpoint" | grep -oE '[0-9]+$')
    if [[ "$port" -lt 1 || "$port" -gt 65535 ]]; then
        return 1
    fi
    
    return 0
}

# 驗證取樣率
validate_sampling_rate() {
    local rate="$1"
    
    if [[ -z "$rate" ]]; then
        return 1
    fi
    
    # 檢查是否為數字
    if ! [[ "$rate" =~ ^[0-9]*\.?[0-9]+$ ]]; then
        return 1
    fi
    
    # 檢查範圍 (0.0 到 1.0)
    if (( $(echo "$rate < 0" | bc -l 2>/dev/null || echo "1") )) || (( $(echo "$rate > 1" | bc -l 2>/dev/null || echo "1") )); then
        return 1
    fi
    
    return 0
}

# 驗證時間間隔 (毫秒)
validate_time_interval() {
    local interval="$1"
    local min_value="${2:-1000}"    # 預設最小值 1 秒
    local max_value="${3:-300000}"  # 預設最大值 5 分鐘
    
    if [[ -z "$interval" ]]; then
        return 1
    fi
    
    # 檢查是否為正整數
    if ! [[ "$interval" =~ ^[0-9]+$ ]]; then
        return 1
    fi
    
    # 檢查範圍
    if [[ "$interval" -lt "$min_value" || "$interval" -gt "$max_value" ]]; then
        return 1
    fi
    
    return 0
}

# 驗證布林值
validate_boolean() {
    local value="$1"
    
    if [[ -z "$value" ]]; then
        return 1
    fi
    
    # 轉換為小寫（相容舊版 bash）
    local lower_value=$(echo "$value" | tr '[:upper:]' '[:lower:]')
    
    case "$lower_value" in
        "true"|"false")
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# 驗證服務名稱格式
validate_service_name() {
    local name="$1"
    
    if [[ -z "$name" ]]; then
        return 1
    fi
    
    # 檢查格式：小寫字母、數字、連字符
    if [[ ! "$name" =~ ^[a-z0-9-]+$ ]]; then
        return 1
    fi
    
    # 檢查長度
    if [[ ${#name} -lt 2 || ${#name} -gt 50 ]]; then
        return 1
    fi
    
    return 0
}

# 驗證日誌級別
validate_log_level() {
    local level="$1"
    
    if [[ -z "$level" ]]; then
        return 1
    fi
    
    # 轉換為大寫（相容舊版 bash）
    local upper_level=$(echo "$level" | tr '[:lower:]' '[:upper:]')
    
    case "$upper_level" in
        "TRACE"|"DEBUG"|"INFO"|"WARN"|"ERROR"|"OFF")
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# 驗證協議類型
validate_protocol() {
    local protocol="$1"
    
    if [[ -z "$protocol" ]]; then
        return 1
    fi
    
    # 轉換為小寫（相容舊版 bash）
    local lower_protocol=$(echo "$protocol" | tr '[:upper:]' '[:lower:]')
    
    case "$lower_protocol" in
        "grpc"|"http/protobuf")
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# 驗證單個配置值
validate_config_value() {
    local key="$1"
    local value="$2"
    local is_valid=true
    local error_message=""
    
    case "$key" in
        "OTEL_EXPORTER_OTLP_ENDPOINT")
            if ! validate_otlp_endpoint "$value"; then
                is_valid=false
                error_message="無效的 OTLP 端點格式: $value"
            fi
            ;;
        "OTEL_TRACES_SAMPLER_ARG")
            if ! validate_sampling_rate "$value"; then
                is_valid=false
                error_message="無效的取樣率 (應該在 0.0-1.0 之間): $value"
            fi
            ;;
        "OTEL_METRIC_EXPORT_INTERVAL"|"OTEL_BSP_EXPORT_TIMEOUT"|"OTEL_BSP_SCHEDULE_DELAY")
            if ! validate_time_interval "$value"; then
                is_valid=false
                error_message="無效的時間間隔 (毫秒): $value"
            fi
            ;;
        "OTEL_BSP_MAX_EXPORT_BATCH_SIZE"|"OTEL_BSP_MAX_QUEUE_SIZE")
            if ! validate_time_interval "$value" 1 100000; then
                is_valid=false
                error_message="無效的批次大小: $value"
            fi
            ;;
        "OTEL_JAVAAGENT_DEBUG"|"OTEL_INSTRUMENTATION_"*"_ENABLED")
            if ! validate_boolean "$value"; then
                is_valid=false
                error_message="無效的布林值 (應該是 true 或 false): $value"
            fi
            ;;
        "OTEL_SERVICE_NAME")
            if ! validate_service_name "$value"; then
                is_valid=false
                error_message="無效的服務名稱格式: $value"
            fi
            ;;
        "OTEL_LOG_LEVEL")
            if ! validate_log_level "$value"; then
                is_valid=false
                error_message="無效的日誌級別: $value"
            fi
            ;;
        "OTEL_EXPORTER_OTLP_PROTOCOL")
            if ! validate_protocol "$value"; then
                is_valid=false
                error_message="無效的協議類型: $value"
            fi
            ;;
    esac
    
    if [[ "$is_valid" == "true" ]]; then
        return 0
    else
        echo "$error_message"
        return 1
    fi
}

# 獲取預設值
get_default_value() {
    local key="$1"
    get_default_config_value "$key"
}

# 修復配置值
fix_config_value() {
    local key="$1"
    local value="$2"
    local fixed_value="$value"
    local was_fixed=false
    
    case "$key" in
        "OTEL_TRACES_SAMPLER_ARG")
            # 如果取樣率無效，使用預設值
            if ! validate_sampling_rate "$value"; then
                if fixed_value=$(get_default_config_value "$key"); then
                    was_fixed=true
                fi
            fi
            ;;
        "OTEL_JAVAAGENT_DEBUG"|"OTEL_INSTRUMENTATION_"*"_ENABLED")
            # 修復布林值
            local lower_value=$(echo "$value" | tr '[:upper:]' '[:lower:]')
            case "$lower_value" in
                "1"|"yes"|"on"|"enabled")
                    fixed_value="true"
                    was_fixed=true
                    ;;
                "0"|"no"|"off"|"disabled")
                    fixed_value="false"
                    was_fixed=true
                    ;;
                *)
                    if ! validate_boolean "$value"; then
                        if default_val=$(get_default_config_value "$key"); then
                            fixed_value="$default_val"
                        else
                            fixed_value="false"
                        fi
                        was_fixed=true
                    fi
                    ;;
            esac
            ;;
        "OTEL_LOG_LEVEL")
            # 修復日誌級別大小寫
            if [[ -n "$value" ]]; then
                local upper_value=$(echo "$value" | tr '[:lower:]' '[:upper:]')
                case "$upper_value" in
                    "TRACE"|"DEBUG"|"INFO"|"WARN"|"ERROR"|"OFF")
                        fixed_value="$upper_value"
                        if [[ "$fixed_value" != "$value" ]]; then
                            was_fixed=true
                        fi
                        ;;
                    *)
                        if default_val=$(get_default_config_value "$key"); then
                            fixed_value="$default_val"
                        else
                            fixed_value="INFO"
                        fi
                        was_fixed=true
                        ;;
                esac
            fi
            ;;
        "OTEL_EXPORTER_OTLP_PROTOCOL")
            # 修復協議格式
            local lower_value=$(echo "$value" | tr '[:upper:]' '[:lower:]')
            case "$lower_value" in
                "grpc"|"http"|"http/protobuf")
                    fixed_value="grpc"
                    if [[ "$fixed_value" != "$value" ]]; then
                        was_fixed=true
                    fi
                    ;;
                *)
                    if default_val=$(get_default_config_value "$key"); then
                        fixed_value="$default_val"
                    else
                        fixed_value="grpc"
                    fi
                    was_fixed=true
                    ;;
            esac
            ;;
    esac
    
    echo "$fixed_value"
    return $([ "$was_fixed" == "true" ] && echo 0 || echo 1)
}

# 驗證配置檔案
validate_config_file() {
    local config_file="$1"
    local strict_mode="${2:-false}"
    local validation_errors=0
    local validation_warnings=0
    
    if [[ ! -f "$config_file" ]]; then
        log_error "配置檔案不存在: $config_file"
        return 1
    fi
    
    log_info "正在驗證配置檔案: $config_file"
    
    # 檢查必要配置項
    for required_config in "${REQUIRED_CONFIGS[@]}"; do
        if ! grep -q "^$required_config=" "$config_file"; then
            if default_val=$(get_default_config_value "$required_config"); then
                log_warning "缺少必要配置項 $required_config，有預設值可用: $default_val"
            else
                log_error "缺少必要配置項: $required_config"
                ((validation_errors++))
            fi
        fi
    done
    
    # 驗證每個配置項
    while IFS='=' read -r key value; do
        # 跳過註解和空行
        if [[ "$key" =~ ^[[:space:]]*# ]] || [[ -z "$key" ]]; then
            continue
        fi
        
        # 移除值的引號
        value=$(echo "$value" | sed 's/^["'\'']\|["'\'']$//g')
        
        # 檢查值是否為空
        if [[ -z "$value" ]]; then
            if default_val=$(get_default_config_value "$key"); then
                if [[ "$strict_mode" == "true" ]]; then
                    log_error "配置項 $key 為空且嚴格模式不允許使用預設值"
                    ((validation_errors++))
                else
                    log_warning "配置項 $key 為空，將使用預設值: $default_val"
                    ((validation_warnings++))
                fi
            else
                log_warning "配置項 $key 為空且沒有預設值"
                ((validation_warnings++))
            fi
            continue
        fi
        
        # 驗證配置值
        if ! error_msg=$(validate_config_value "$key" "$value" 2>&1); then
            log_error "$error_msg"
            ((validation_errors++))
        fi
        
    done < <(grep -E '^[A-Z_]+=.*' "$config_file" || true)
    
    # 顯示驗證結果
    if [[ "$validation_errors" -eq 0 ]]; then
        if [[ "$validation_warnings" -eq 0 ]]; then
            log_success "配置檔案驗證通過"
        else
            log_success "配置檔案驗證通過 (有 $validation_warnings 個警告)"
        fi
        return 0
    else
        log_error "配置檔案驗證失敗 ($validation_errors 個錯誤, $validation_warnings 個警告)"
        return 1
    fi
}

# 修復配置檔案
fix_config_file() {
    local input_file="$1"
    local output_file="${2:-$input_file}"
    local fixes_applied=0
    
    if [[ ! -f "$input_file" ]]; then
        log_error "配置檔案不存在: $input_file"
        return 1
    fi
    
    log_info "正在修復配置檔案: $input_file"
    
    # 創建臨時檔案
    local temp_file=$(mktemp)
    
    # 處理每一行
    while IFS= read -r line; do
        # 保留註解和空行
        if [[ "$line" =~ ^[[:space:]]*# ]] || [[ -z "$line" ]]; then
            echo "$line" >> "$temp_file"
            continue
        fi
        
        # 處理配置行
        if [[ "$line" =~ ^([A-Z_]+)=(.*)$ ]]; then
            local key="${BASH_REMATCH[1]}"
            local value="${BASH_REMATCH[2]}"
            
            # 移除值的引號
            value=$(echo "$value" | sed 's/^["'\'']\|["'\'']$//g')
            
            # 如果值為空，嘗試使用預設值
            if [[ -z "$value" ]] && default_val=$(get_default_config_value "$key"); then
                value="$default_val"
                log_info "為 $key 設定預設值: $value"
                ((fixes_applied++))
            fi
            
            # 嘗試修復值
            if [[ -n "$value" ]]; then
                local fixed_value
                if fixed_value=$(fix_config_value "$key" "$value") && [[ "$fixed_value" != "$value" ]]; then
                    log_info "修復 $key: $value -> $fixed_value"
                    value="$fixed_value"
                    ((fixes_applied++))
                fi
            fi
            
            echo "$key=$value" >> "$temp_file"
        else
            echo "$line" >> "$temp_file"
        fi
    done < "$input_file"
    
    # 添加缺少的必要配置項
    for required_config in "${REQUIRED_CONFIGS[@]}"; do
        if ! grep -q "^$required_config=" "$temp_file"; then
            if default_val=$(get_default_config_value "$required_config"); then
                echo "$required_config=$default_val" >> "$temp_file"
                log_info "添加缺少的配置項: $required_config=$default_val"
                ((fixes_applied++))
            else
                log_warning "無法添加必要配置項 $required_config (沒有預設值)"
            fi
        fi
    done
    
    # 移動臨時檔案到輸出位置
    mv "$temp_file" "$output_file"
    
    if [[ "$fixes_applied" -gt 0 ]]; then
        log_success "配置檔案已修復，應用了 $fixes_applied 個修復"
    else
        log_info "配置檔案無需修復"
    fi
    
    return 0
}

# 列出所有預設值
list_defaults() {
    log_info "OpenTelemetry 配置預設值:"
    
    # 列出所有支援的配置項及其預設值
    local configs=(
        "OTEL_SERVICE_VERSION"
        "OTEL_EXPORTER_OTLP_PROTOCOL"
        "OTEL_TRACES_SAMPLER"
        "OTEL_TRACES_SAMPLER_ARG"
        "OTEL_METRICS_EXPORTER"
        "OTEL_TRACES_EXPORTER"
        "OTEL_LOGS_EXPORTER"
        "OTEL_METRIC_EXPORT_INTERVAL"
        "OTEL_JAVAAGENT_DEBUG"
        "OTEL_LOG_LEVEL"
        "OTEL_BSP_MAX_EXPORT_BATCH_SIZE"
        "OTEL_BSP_EXPORT_TIMEOUT"
        "OTEL_BSP_SCHEDULE_DELAY"
        "OTEL_BSP_MAX_QUEUE_SIZE"
        "OTEL_EXPORTER_OTLP_COMPRESSION"
        "OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED"
        "JAVA_TOOL_OPTIONS"
        "OTEL_JAVAAGENT_CONFIGURATION_FILE"
    )
    
    for key in "${configs[@]}"; do
        if default_val=$(get_default_config_value "$key"); then
            echo "  $key=$default_val"
        fi
    done
}

# 主函數
main() {
    local verbose=false
    local strict_mode=false
    local output_file=""
    
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
            --strict)
                strict_mode=true
                shift
                ;;
            --output)
                output_file="$2"
                shift 2
                ;;
            validate)
                if [[ $# -lt 2 ]]; then
                    log_error "validate 命令需要配置檔案參數"
                    exit 1
                fi
                validate_config_file "$2" "$strict_mode"
                exit $?
                ;;
            fix)
                if [[ $# -lt 2 ]]; then
                    log_error "fix 命令需要配置檔案參數"
                    exit 1
                fi
                fix_config_file "$2" "$output_file"
                exit $?
                ;;
            check-value)
                if [[ $# -lt 3 ]]; then
                    log_error "check-value 命令需要鍵和值參數"
                    exit 1
                fi
                if validate_config_value "$2" "$3"; then
                    log_success "配置值有效: $2=$3"
                    exit 0
                else
                    exit 1
                fi
                ;;
            get-default)
                if [[ $# -lt 2 ]]; then
                    log_error "get-default 命令需要鍵參數"
                    exit 1
                fi
                if default_value=$(get_default_value "$2"); then
                    echo "$default_value"
                    exit 0
                else
                    log_error "沒有找到 $2 的預設值"
                    exit 1
                fi
                ;;
            list-defaults)
                list_defaults
                exit 0
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