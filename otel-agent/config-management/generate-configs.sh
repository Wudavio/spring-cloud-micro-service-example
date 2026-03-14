#!/bin/bash

# OpenTelemetry 配置生成器
# 根據環境和服務模板生成最終的配置檔案

set -e

# 腳本目錄
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENVIRONMENTS_DIR="$SCRIPT_DIR/environments"
SERVICES_DIR="$SCRIPT_DIR/services"
OUTPUT_DIR="$SCRIPT_DIR/../generated-configs"

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

# 顯示使用說明
show_help() {
    cat << EOF
OpenTelemetry 配置生成器

使用方式:
    $0 [選項] <環境> [服務名稱]

參數:
    環境        目標環境 (development, testing, staging, production)
    服務名稱    特定服務名稱 (可選，不指定則生成所有服務)

選項:
    -h, --help              顯示此說明
    -o, --output-dir <目錄> 指定輸出目錄 (預設: $OUTPUT_DIR)
    --service-version <版本> 設定服務版本 (預設: 1.0.0)
    --clean                 清理輸出目錄

範例:
    $0 development                    # 生成開發環境所有服務配置
    $0 production product-service     # 生成生產環境 product-service 配置
    $0 --clean development            # 清理並生成開發環境配置

EOF
}

# 清理輸出目錄
clean_output_dir() {
    if [[ -d "$OUTPUT_DIR" ]]; then
        log_info "正在清理輸出目錄: $OUTPUT_DIR"
        rm -rf "$OUTPUT_DIR"
    fi
    mkdir -p "$OUTPUT_DIR"
}

# 生成單個服務的配置
generate_service_config() {
    local environment=$1
    local service=$2
    local service_version=${3:-"1.0.0"}
    
    local env_file="$ENVIRONMENTS_DIR/$environment.env"
    local service_template="$SERVICES_DIR/$service.template"
    local output_file="$OUTPUT_DIR/${service}-${environment}.env"
    
    # 檢查環境檔案是否存在
    if [[ ! -f "$env_file" ]]; then
        log_error "環境配置檔案不存在: $env_file"
        return 1
    fi
    
    # 檢查服務模板是否存在
    if [[ ! -f "$service_template" ]]; then
        log_warning "服務模板不存在: $service_template，使用通用模板"
        service_template="$SERVICES_DIR/generic.template"
        
        # 如果通用模板也不存在，創建一個
        if [[ ! -f "$service_template" ]]; then
            create_generic_template
        fi
    fi
    
    log_info "正在生成 $service 的 $environment 環境配置..."
    
    # 創建輸出檔案
    cat > "$output_file" << EOF
# $service OpenTelemetry 配置 - $environment 環境
# 自動生成於 $(date)
# 
# 此檔案由以下模板組合而成:
# - 環境配置: $env_file
# - 服務模板: $service_template

EOF
    
    # 載入環境變數
    source "$env_file"
    
    # 設定服務版本
    export SERVICE_VERSION="$service_version"
    
    # 處理服務模板並替換變數
    while IFS= read -r line; do
        # 跳過註解行和空行
        if [[ "$line" =~ ^[[:space:]]*# ]] || [[ -z "$line" ]]; then
            echo "$line" >> "$output_file"
            continue
        fi
        
        # 替換變數
        processed_line="$line"
        # 替換 ${SERVICE_VERSION:-1.0.0} 格式的變數
        processed_line="${processed_line//\$\{SERVICE_VERSION:-1.0.0\}/$SERVICE_VERSION}"
        # 替換 ${SERVICE_NAME} 格式的變數
        processed_line="${processed_line//\$\{SERVICE_NAME\}/$service}"
        # 替換 ${DEPLOYMENT_ENVIRONMENT} 格式的變數
        processed_line="${processed_line//\$\{DEPLOYMENT_ENVIRONMENT\}/$DEPLOYMENT_ENVIRONMENT}"
        
        echo "$processed_line" >> "$output_file"
    done < "$service_template"
    
    # 添加環境特定配置
    echo "" >> "$output_file"
    echo "# 環境特定配置" >> "$output_file"
    
    # 從環境檔案中提取配置
    while IFS= read -r line; do
        # 跳過註解行和空行
        if [[ "$line" =~ ^[[:space:]]*# ]] || [[ -z "$line" ]]; then
            continue
        fi
        
        # 跳過已經在服務模板中定義的變數
        var_name=$(echo "$line" | cut -d'=' -f1)
        if grep -q "^$var_name=" "$service_template" 2>/dev/null; then
            continue
        fi
        
        echo "$line" >> "$output_file"
    done < "$env_file"
    
    # 添加通用配置
    cat >> "$output_file" << EOF

# 通用 OpenTelemetry 配置
OTEL_METRICS_EXPORTER=otlp
OTEL_TRACES_EXPORTER=otlp
OTEL_LOGS_EXPORTER=otlp

# 跨服務追蹤關聯配置
OTEL_PROPAGATORS=tracecontext,baggage,b3,b3multi
OTEL_PROPAGATORS_DEFAULT=tracecontext,baggage

# Java Agent 配置
OTEL_JAVAAGENT_CONFIGURATION_FILE=/app/otel-config.properties
JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar

# 最終資源屬性組合
OTEL_RESOURCE_ATTRIBUTES=\${OTEL_RESOURCE_ATTRIBUTES_BASE:-deployment.environment=$DEPLOYMENT_ENVIRONMENT,service.namespace=microservices},\${OTEL_RESOURCE_ATTRIBUTES_SERVICE:-service.name=$service},service.version=$SERVICE_VERSION,service.instance.id=${service}-\${HOSTNAME:-1}

EOF
    
    log_success "配置檔案已生成: $output_file"
}

# 創建通用服務模板
create_generic_template() {
    local generic_template="$SERVICES_DIR/generic.template"
    
    cat > "$generic_template" << EOF
# 通用服務配置模板
# 此檔案用於沒有特定模板的服務

# 服務識別
OTEL_SERVICE_NAME=\${SERVICE_NAME}
OTEL_SERVICE_VERSION=\${SERVICE_VERSION:-1.0.0}

# 基本 HTTP 儀表化
OTEL_INSTRUMENTATION_RESTTEMPLATE_ENABLED=true
OTEL_INSTRUMENTATION_APACHE_HTTPCLIENT_ENABLED=true
OTEL_INSTRUMENTATION_SPRING_WEB_ENABLED=true
OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED=true

# 資料庫儀表化
OTEL_INSTRUMENTATION_JDBC_ENABLED=true
OTEL_INSTRUMENTATION_HIBERNATE_ENABLED=true
OTEL_INSTRUMENTATION_JPA_ENABLED=true

# HTTP 標頭傳播配置
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_CLIENT_REQUEST=traceparent,tracestate,baggage,x-trace-id,x-span-id
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_CLIENT_RESPONSE=x-trace-id,x-span-id
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_REQUEST=traceparent,tracestate,baggage,x-trace-id,x-span-id,user-agent
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_RESPONSE=x-trace-id,x-span-id

# 資源屬性
OTEL_RESOURCE_ATTRIBUTES_SERVICE=service.name=\${SERVICE_NAME},service.role=generic,service.tier=application
EOF
    
    log_info "已創建通用服務模板: $generic_template"
}

# 獲取所有支援的服務
get_all_services() {
    local services=()
    
    # 從服務模板目錄獲取服務列表
    for template in "$SERVICES_DIR"/*.template; do
        if [[ -f "$template" ]]; then
            local service=$(basename "$template" .template)
            if [[ "$service" != "generic" ]]; then
                services+=("$service")
            fi
        fi
    done
    
    echo "${services[@]}"
}

# 主函數
main() {
    local environment=""
    local service=""
    local service_version="1.0.0"
    local clean_first=false
    
    # 解析命令列參數
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -o|--output-dir)
                OUTPUT_DIR="$2"
                shift 2
                ;;
            --service-version)
                service_version="$2"
                shift 2
                ;;
            --clean)
                clean_first=true
                shift
                ;;
            -*)
                log_error "未知的選項: $1"
                show_help
                exit 1
                ;;
            *)
                if [[ -z "$environment" ]]; then
                    environment="$1"
                elif [[ -z "$service" ]]; then
                    service="$1"
                else
                    log_error "過多的參數: $1"
                    show_help
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # 檢查必要參數
    if [[ -z "$environment" ]]; then
        log_error "請指定環境"
        show_help
        exit 1
    fi
    
    # 檢查環境是否存在
    if [[ ! -f "$ENVIRONMENTS_DIR/$environment.env" ]]; then
        log_error "環境配置不存在: $environment"
        log_info "可用的環境:"
        for env_file in "$ENVIRONMENTS_DIR"/*.env; do
            if [[ -f "$env_file" ]]; then
                local env_name=$(basename "$env_file" .env)
                echo "  - $env_name"
            fi
        done
        exit 1
    fi
    
    # 清理輸出目錄
    if [[ "$clean_first" == true ]]; then
        clean_output_dir
    else
        mkdir -p "$OUTPUT_DIR"
    fi
    
    # 生成配置
    if [[ -n "$service" ]]; then
        # 生成單個服務配置
        generate_service_config "$environment" "$service" "$service_version"
    else
        # 生成所有服務配置
        log_info "正在生成 $environment 環境的所有服務配置..."
        
        local services=($(get_all_services))
        
        if [[ ${#services[@]} -eq 0 ]]; then
            log_error "沒有找到任何服務模板"
            exit 1
        fi
        
        for svc in "${services[@]}"; do
            generate_service_config "$environment" "$svc" "$service_version"
        done
        
        log_success "所有服務配置已生成完成"
    fi
    
    log_info "配置檔案輸出目錄: $OUTPUT_DIR"
}

# 執行主函數
main "$@"