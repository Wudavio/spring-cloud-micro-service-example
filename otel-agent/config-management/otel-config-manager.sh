#!/bin/bash

# OpenTelemetry 配置管理工具
# 用於生成和管理不同環境的 OpenTelemetry 配置

set -e

# 預設值
DEFAULT_ENVIRONMENT="development"
DEFAULT_SERVICE_VERSION="1.0.0"
DEFAULT_SERVICE_NAMESPACE="microservices"

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
OpenTelemetry 配置管理工具

使用方式:
    $0 [選項] <命令> [參數]

命令:
    generate-env <服務名稱> <環境>    生成指定服務和環境的配置檔案
    validate-config <配置檔案>        驗證配置檔案的正確性
    list-services                     列出所有支援的服務
    list-environments                 列出所有支援的環境
    update-all <環境>                 更新所有服務的配置檔案

選項:
    -h, --help                        顯示此說明
    -v, --verbose                     詳細輸出
    --service-version <版本>          設定服務版本 (預設: $DEFAULT_SERVICE_VERSION)
    --namespace <命名空間>            設定服務命名空間 (預設: $DEFAULT_SERVICE_NAMESPACE)

範例:
    $0 generate-env product-service development
    $0 validate-config ../product-service.env
    $0 update-all production

EOF
}

# 支援的服務列表
SUPPORTED_SERVICES=(
    "api-gateway"
    "auth-service"
    "config-server"
    "eureka-server"
    "inventory-service"
    "order-service"
    "product-service"
)

# 支援的環境列表
SUPPORTED_ENVIRONMENTS=(
    "development"
    "testing"
    "staging"
    "production"
)

# 檢查服務是否支援
is_service_supported() {
    local service=$1
    for supported_service in "${SUPPORTED_SERVICES[@]}"; do
        if [[ "$supported_service" == "$service" ]]; then
            return 0
        fi
    done
    return 1
}

# 檢查環境是否支援
is_environment_supported() {
    local environment=$1
    for supported_env in "${SUPPORTED_ENVIRONMENTS[@]}"; do
        if [[ "$supported_env" == "$environment" ]]; then
            return 0
        fi
    done
    return 1
}

# 獲取環境特定的配置
get_environment_config() {
    local environment=$1
    
    case "$environment" in
        "development")
            echo "OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317"
            echo "OTEL_TRACES_SAMPLER_ARG=1.0"
            echo "OTEL_METRIC_EXPORT_INTERVAL=10000"
            echo "OTEL_JAVAAGENT_DEBUG=true"
            echo "OTEL_RESOURCE_ATTRIBUTES=deployment.environment=development,service.namespace=$SERVICE_NAMESPACE"
            ;;
        "testing")
            echo "OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector-test:4317"
            echo "OTEL_TRACES_SAMPLER_ARG=0.5"
            echo "OTEL_METRIC_EXPORT_INTERVAL=15000"
            echo "OTEL_JAVAAGENT_DEBUG=false"
            echo "OTEL_RESOURCE_ATTRIBUTES=deployment.environment=testing,service.namespace=$SERVICE_NAMESPACE"
            ;;
        "staging")
            echo "OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector-staging:4317"
            echo "OTEL_TRACES_SAMPLER_ARG=0.1"
            echo "OTEL_METRIC_EXPORT_INTERVAL=30000"
            echo "OTEL_JAVAAGENT_DEBUG=false"
            echo "OTEL_RESOURCE_ATTRIBUTES=deployment.environment=staging,service.namespace=$SERVICE_NAMESPACE"
            ;;
        "production")
            echo "OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317"
            echo "OTEL_TRACES_SAMPLER_ARG=0.01"
            echo "OTEL_METRIC_EXPORT_INTERVAL=60000"
            echo "OTEL_JAVAAGENT_DEBUG=false"
            echo "OTEL_RESOURCE_ATTRIBUTES=deployment.environment=production,service.namespace=$SERVICE_NAMESPACE"
            ;;
        *)
            log_error "不支援的環境: $environment"
            return 1
            ;;
    esac
}

# 獲取服務特定的配置
get_service_config() {
    local service=$1
    
    case "$service" in
        "api-gateway")
            echo "# API Gateway 特定配置"
            echo "OTEL_INSTRUMENTATION_SPRING_CLOUD_GATEWAY_ENABLED=true"
            echo "OTEL_INSTRUMENTATION_REACTOR_NETTY_ENABLED=true"
            echo "OTEL_INSTRUMENTATION_NETTY_ENABLED=true"
            echo "OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING=product-service=product-service,order-service=order-service,inventory-service=inventory-service,auth-service=auth-service"
            ;;
        "auth-service")
            echo "# Auth Service 特定配置"
            echo "OTEL_INSTRUMENTATION_SPRING_SECURITY_ENABLED=true"
            ;;
        "config-server")
            echo "# Config Server 特定配置"
            echo "OTEL_INSTRUMENTATION_SPRING_CLOUD_CONFIG_ENABLED=true"
            ;;
        "inventory-service")
            echo "# Inventory Service 特定配置"
            echo "OTEL_INSTRUMENTATION_JEDIS_ENABLED=true"
            echo "OTEL_INSTRUMENTATION_LETTUCE_ENABLED=true"
            ;;
        "order-service")
            echo "# Order Service 特定配置"
            echo "OTEL_INSTRUMENTATION_OPENFEIGN_ENABLED=true"
            echo "OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING=product-service=product-service,inventory-service=inventory-service,auth-service=auth-service"
            ;;
        *)
            echo "# $service 通用配置"
            ;;
    esac
}

# 生成環境變數配置檔案
generate_env_file() {
    local service=$1
    local environment=$2
    local output_file="../${service}-${environment}.env"
    
    log_info "正在生成 $service 的 $environment 環境配置檔案..."
    
    # 檢查服務和環境是否支援
    if ! is_service_supported "$service"; then
        log_error "不支援的服務: $service"
        return 1
    fi
    
    if ! is_environment_supported "$environment"; then
        log_error "不支援的環境: $environment"
        return 1
    fi
    
    # 生成配置檔案
    cat > "$output_file" << EOF
# $service OpenTelemetry 配置 - $environment 環境
# 自動生成於 $(date)

# 基本服務配置
OTEL_SERVICE_NAME=$service
OTEL_SERVICE_VERSION=$SERVICE_VERSION

# 環境特定配置
$(get_environment_config "$environment")

# OpenTelemetry 協議配置
OTEL_EXPORTER_OTLP_PROTOCOL=grpc

# 指標配置
OTEL_METRICS_EXPORTER=otlp

# 追蹤配置
OTEL_TRACES_EXPORTER=otlp
OTEL_TRACES_SAMPLER=traceidratio

# 跨服務追蹤關聯配置
OTEL_PROPAGATORS=tracecontext,baggage,b3,b3multi
OTEL_PROPAGATORS_DEFAULT=tracecontext,baggage

# HTTP 標頭傳播配置
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_CLIENT_REQUEST=traceparent,tracestate,baggage,x-trace-id,x-span-id
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_CLIENT_RESPONSE=x-trace-id,x-span-id
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_REQUEST=traceparent,tracestate,baggage,x-trace-id,x-span-id,user-agent
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_RESPONSE=x-trace-id,x-span-id

# 服務間通信儀表化
OTEL_INSTRUMENTATION_RESTTEMPLATE_ENABLED=true
OTEL_INSTRUMENTATION_SPRING_WEBFLUX_ENABLED=true
OTEL_INSTRUMENTATION_APACHE_HTTPCLIENT_ENABLED=true

$(get_service_config "$service")

# 日誌配置
OTEL_LOGS_EXPORTER=otlp

# Java Agent 配置
OTEL_JAVAAGENT_CONFIGURATION_FILE=/app/otel-config.properties
JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar

EOF
    
    log_success "配置檔案已生成: $output_file"
}

# 驗證配置檔案
validate_config() {
    local config_file=$1
    
    if [[ ! -f "$config_file" ]]; then
        log_error "配置檔案不存在: $config_file"
        return 1
    fi
    
    log_info "正在驗證配置檔案: $config_file"
    
    # 檢查必要的配置項
    local required_vars=(
        "OTEL_SERVICE_NAME"
        "OTEL_EXPORTER_OTLP_ENDPOINT"
        "OTEL_TRACES_EXPORTER"
        "OTEL_METRICS_EXPORTER"
        "OTEL_LOGS_EXPORTER"
    )
    
    local missing_vars=()
    
    for var in "${required_vars[@]}"; do
        if ! grep -q "^$var=" "$config_file"; then
            missing_vars+=("$var")
        fi
    done
    
    if [[ ${#missing_vars[@]} -gt 0 ]]; then
        log_error "缺少必要的配置項:"
        for var in "${missing_vars[@]}"; do
            echo "  - $var"
        done
        return 1
    fi
    
    # 檢查端點格式
    local endpoint=$(grep "^OTEL_EXPORTER_OTLP_ENDPOINT=" "$config_file" | cut -d'=' -f2)
    if [[ ! "$endpoint" =~ ^https?:// ]]; then
        log_warning "OTLP 端點格式可能不正確: $endpoint"
    fi
    
    # 檢查取樣率
    local sampling_arg=$(grep "^OTEL_TRACES_SAMPLER_ARG=" "$config_file" | cut -d'=' -f2)
    if [[ -n "$sampling_arg" ]]; then
        if ! [[ "$sampling_arg" =~ ^[0-9]*\.?[0-9]+$ ]] || (( $(echo "$sampling_arg > 1" | bc -l) )); then
            log_warning "取樣率應該在 0.0 到 1.0 之間: $sampling_arg"
        fi
    fi
    
    log_success "配置檔案驗證通過"
}

# 列出支援的服務
list_services() {
    log_info "支援的服務:"
    for service in "${SUPPORTED_SERVICES[@]}"; do
        echo "  - $service"
    done
}

# 列出支援的環境
list_environments() {
    log_info "支援的環境:"
    for env in "${SUPPORTED_ENVIRONMENTS[@]}"; do
        echo "  - $env"
    done
}

# 更新所有服務的配置
update_all() {
    local environment=$1
    
    if ! is_environment_supported "$environment"; then
        log_error "不支援的環境: $environment"
        return 1
    fi
    
    log_info "正在更新所有服務的 $environment 環境配置..."
    
    for service in "${SUPPORTED_SERVICES[@]}"; do
        generate_env_file "$service" "$environment"
    done
    
    log_success "所有服務的配置檔案已更新完成"
}

# 主函數
main() {
    # 預設值
    SERVICE_VERSION="$DEFAULT_SERVICE_VERSION"
    SERVICE_NAMESPACE="$DEFAULT_SERVICE_NAMESPACE"
    VERBOSE=false
    
    # 解析命令列參數
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            --service-version)
                SERVICE_VERSION="$2"
                shift 2
                ;;
            --namespace)
                SERVICE_NAMESPACE="$2"
                shift 2
                ;;
            generate-env)
                if [[ $# -lt 3 ]]; then
                    log_error "generate-env 命令需要服務名稱和環境參數"
                    exit 1
                fi
                generate_env_file "$2" "$3"
                exit $?
                ;;
            validate-config)
                if [[ $# -lt 2 ]]; then
                    log_error "validate-config 命令需要配置檔案路徑"
                    exit 1
                fi
                validate_config "$2"
                exit $?
                ;;
            list-services)
                list_services
                exit 0
                ;;
            list-environments)
                list_environments
                exit 0
                ;;
            update-all)
                if [[ $# -lt 2 ]]; then
                    log_error "update-all 命令需要環境參數"
                    exit 1
                fi
                update_all "$2"
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