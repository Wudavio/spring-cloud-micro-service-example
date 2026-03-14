#!/bin/bash

# OpenTelemetry 配置驗證工具
# 驗證生成的配置檔案是否正確

set -e

# 腳本目錄
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIGS_DIR="$SCRIPT_DIR/../generated-configs"

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

# 驗證統計
total_files=0
valid_files=0
invalid_files=0
warnings=0

# 必要的配置項
REQUIRED_VARS=(
    "OTEL_SERVICE_NAME"
    "OTEL_SERVICE_VERSION"
    "OTEL_EXPORTER_OTLP_ENDPOINT"
    "OTEL_EXPORTER_OTLP_PROTOCOL"
    "OTEL_TRACES_EXPORTER"
    "OTEL_METRICS_EXPORTER"
    "OTEL_LOGS_EXPORTER"
    "OTEL_RESOURCE_ATTRIBUTES"
    "JAVA_TOOL_OPTIONS"
)

# 驗證單個配置檔案
validate_config_file() {
    local config_file=$1
    local file_valid=true
    local file_warnings=0
    
    log_info "正在驗證: $(basename "$config_file")"
    
    # 檢查檔案是否存在
    if [[ ! -f "$config_file" ]]; then
        log_error "配置檔案不存在: $config_file"
        return 1
    fi
    
    # 檢查必要的配置項
    local missing_vars=()
    
    for var in "${REQUIRED_VARS[@]}"; do
        if ! grep -q "^$var=" "$config_file"; then
            missing_vars+=("$var")
        fi
    done
    
    if [[ ${#missing_vars[@]} -gt 0 ]]; then
        log_error "缺少必要的配置項:"
        for var in "${missing_vars[@]}"; do
            echo "    - $var"
        done
        file_valid=false
    fi
    
    # 檢查 OTLP 端點格式
    local endpoint=$(grep "^OTEL_EXPORTER_OTLP_ENDPOINT=" "$config_file" 2>/dev/null | cut -d'=' -f2 | tr -d '"' || echo "")
    if [[ -n "$endpoint" ]]; then
        if [[ ! "$endpoint" =~ ^https?:// ]]; then
            log_warning "OTLP 端點格式可能不正確: $endpoint"
            ((file_warnings++))
        fi
        
        # 檢查端點是否包含埠號
        if [[ ! "$endpoint" =~ :[0-9]+$ ]] && [[ ! "$endpoint" =~ :[0-9]+/ ]]; then
            log_warning "OTLP 端點缺少埠號: $endpoint"
            ((file_warnings++))
        fi
    fi
    
    # 檢查取樣率
    local sampling_arg=$(grep "^OTEL_TRACES_SAMPLER_ARG=" "$config_file" 2>/dev/null | cut -d'=' -f2 | tr -d '"' || echo "")
    if [[ -n "$sampling_arg" ]]; then
        if ! [[ "$sampling_arg" =~ ^[0-9]*\.?[0-9]+$ ]]; then
            log_error "取樣率格式不正確: $sampling_arg"
            file_valid=false
        elif (( $(echo "$sampling_arg > 1" | bc -l 2>/dev/null || echo "0") )); then
            log_warning "取樣率大於 1.0: $sampling_arg"
            ((file_warnings++))
        elif (( $(echo "$sampling_arg < 0" | bc -l 2>/dev/null || echo "0") )); then
            log_error "取樣率小於 0.0: $sampling_arg"
            file_valid=false
        fi
    fi
    
    # 檢查服務名稱格式
    local service_name=$(grep "^OTEL_SERVICE_NAME=" "$config_file" 2>/dev/null | cut -d'=' -f2 | tr -d '"' || echo "")
    if [[ -n "$service_name" ]]; then
        if [[ ! "$service_name" =~ ^[a-z0-9-]+$ ]]; then
            log_warning "服務名稱格式建議使用小寫字母、數字和連字符: $service_name"
            ((file_warnings++))
        fi
    fi
    
    # 檢查協議設定
    local protocol=$(grep "^OTEL_EXPORTER_OTLP_PROTOCOL=" "$config_file" 2>/dev/null | cut -d'=' -f2 | tr -d '"' || echo "")
    if [[ -n "$protocol" ]]; then
        if [[ "$protocol" != "grpc" && "$protocol" != "http/protobuf" ]]; then
            log_warning "不建議的 OTLP 協議: $protocol (建議使用 grpc 或 http/protobuf)"
            ((file_warnings++))
        fi
    fi
    
    # 檢查 Java Agent 配置
    local java_opts=$(grep "^JAVA_TOOL_OPTIONS=" "$config_file" 2>/dev/null | cut -d'=' -f2 | tr -d '"' || echo "")
    if [[ -n "$java_opts" ]]; then
        if [[ ! "$java_opts" =~ -javaagent: ]]; then
            log_error "JAVA_TOOL_OPTIONS 缺少 -javaagent 參數"
            file_valid=false
        fi
    fi
    
    # 檢查資源屬性格式
    local resource_attrs=$(grep "^OTEL_RESOURCE_ATTRIBUTES=" "$config_file" 2>/dev/null | cut -d'=' -f2- | tr -d '"' || echo "")
    if [[ -n "$resource_attrs" ]]; then
        # 檢查是否包含必要的屬性
        if [[ ! "$resource_attrs" =~ service\.name= ]]; then
            log_warning "資源屬性缺少 service.name"
            ((file_warnings++))
        fi
        if [[ ! "$resource_attrs" =~ deployment\.environment= ]]; then
            log_warning "資源屬性缺少 deployment.environment"
            ((file_warnings++))
        fi
    fi
    
    # 檢查導出間隔
    local metric_interval=$(grep "^OTEL_METRIC_EXPORT_INTERVAL=" "$config_file" 2>/dev/null | cut -d'=' -f2 | tr -d '"' || echo "")
    if [[ -n "$metric_interval" ]]; then
        if ! [[ "$metric_interval" =~ ^[0-9]+$ ]]; then
            log_error "指標導出間隔格式不正確: $metric_interval"
            file_valid=false
        elif [[ "$metric_interval" -lt 1000 ]]; then
            log_warning "指標導出間隔過短 (< 1秒): $metric_interval ms"
            ((file_warnings++))
        elif [[ "$metric_interval" -gt 300000 ]]; then
            log_warning "指標導出間隔過長 (> 5分鐘): $metric_interval ms"
            ((file_warnings++))
        fi
    fi
    
    # 檢查重複的配置項
    local duplicate_vars=$(grep -E "^[A-Z_]+=.*" "$config_file" | cut -d'=' -f1 | sort | uniq -d)
    if [[ -n "$duplicate_vars" ]]; then
        log_warning "發現重複的配置項:"
        echo "$duplicate_vars" | while read -r var; do
            echo "    - $var"
        done
        ((file_warnings++))
    fi
    
    # 更新統計
    ((total_files++))
    warnings=$((warnings + file_warnings))
    
    if [[ "$file_valid" == true ]]; then
        ((valid_files++))
        if [[ "$file_warnings" -eq 0 ]]; then
            log_success "驗證通過"
        else
            log_success "驗證通過 (有 $file_warnings 個警告)"
        fi
    else
        ((invalid_files++))
        log_error "驗證失敗"
    fi
    
    echo ""
    
    return $([[ "$file_valid" == true ]] && echo 0 || echo 1)
}

# 驗證所有配置檔案
validate_all_configs() {
    log_info "開始驗證所有配置檔案..."
    echo ""
    
    if [[ ! -d "$CONFIGS_DIR" ]]; then
        log_error "配置目錄不存在: $CONFIGS_DIR"
        log_info "請先執行 generate-configs.sh 生成配置檔案"
        exit 1
    fi
    
    local config_files=("$CONFIGS_DIR"/*.env)
    
    if [[ ${#config_files[@]} -eq 0 ]] || [[ ! -f "${config_files[0]}" ]]; then
        log_error "沒有找到任何配置檔案"
        log_info "請先執行 generate-configs.sh 生成配置檔案"
        exit 1
    fi
    
    local overall_result=0
    
    for config_file in "${config_files[@]}"; do
        if [[ -f "$config_file" ]]; then
            validate_config_file "$config_file" || overall_result=1
        fi
    done
    
    # 顯示總結
    echo "=================================="
    log_info "驗證總結:"
    echo "  總檔案數: $total_files"
    echo "  有效檔案: $valid_files"
    echo "  無效檔案: $invalid_files"
    echo "  警告總數: $warnings"
    
    if [[ "$invalid_files" -eq 0 ]]; then
        log_success "所有配置檔案驗證通過!"
        if [[ "$warnings" -gt 0 ]]; then
            log_warning "但有 $warnings 個警告需要注意"
        fi
    else
        log_error "$invalid_files 個配置檔案驗證失敗"
        overall_result=1
    fi
    
    return $overall_result
}

# 顯示使用說明
show_help() {
    cat << EOF
OpenTelemetry 配置驗證工具

使用方式:
    $0 [選項] [配置檔案]

參數:
    配置檔案    要驗證的特定配置檔案 (可選，不指定則驗證所有檔案)

選項:
    -h, --help              顯示此說明
    -d, --configs-dir <目錄> 指定配置檔案目錄 (預設: $CONFIGS_DIR)

範例:
    $0                                    # 驗證所有配置檔案
    $0 product-service-development.env    # 驗證特定配置檔案
    $0 -d /path/to/configs               # 指定配置目錄

EOF
}

# 主函數
main() {
    local specific_file=""
    
    # 解析命令列參數
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -d|--configs-dir)
                CONFIGS_DIR="$2"
                shift 2
                ;;
            -*)
                log_error "未知的選項: $1"
                show_help
                exit 1
                ;;
            *)
                if [[ -z "$specific_file" ]]; then
                    specific_file="$1"
                else
                    log_error "過多的參數: $1"
                    show_help
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # 檢查 bc 命令是否可用 (用於數值比較)
    if ! command -v bc &> /dev/null; then
        log_warning "bc 命令不可用，將跳過數值驗證"
    fi
    
    if [[ -n "$specific_file" ]]; then
        # 驗證特定檔案
        if [[ ! -f "$specific_file" ]]; then
            # 嘗試在配置目錄中查找
            local full_path="$CONFIGS_DIR/$specific_file"
            if [[ -f "$full_path" ]]; then
                specific_file="$full_path"
            else
                log_error "配置檔案不存在: $specific_file"
                exit 1
            fi
        fi
        
        validate_config_file "$specific_file"
    else
        # 驗證所有檔案
        validate_all_configs
    fi
}

# 執行主函數
main "$@"