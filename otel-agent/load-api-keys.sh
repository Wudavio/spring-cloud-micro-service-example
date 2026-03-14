#!/bin/bash

# OpenTelemetry API 金鑰載入腳本
# 用於在容器啟動時載入 API 金鑰到環境變數

set -e

echo "=== 載入 OpenTelemetry API 金鑰 ==="
echo "開始時間: $(date)"
echo

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# API 金鑰檔案路徑
API_KEY_DIR="/api-keys"
ENV_FILE="/tmp/api-keys.env"

# 服務列表和對應的環境變數名稱
declare -A SERVICES=(
    ["otel-collector"]="OTEL_API_KEY"
    ["mimir"]="MIMIR_API_KEY"
    ["tempo"]="TEMPO_API_KEY"
    ["loki"]="LOKI_API_KEY"
    ["grafana"]="GRAFANA_API_KEY"
)

# 記錄函數
log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] [$level] $message"
}

# 驗證 API 金鑰格式
validate_api_key() {
    local api_key=$1
    local service_name=$2
    
    # 檢查金鑰長度（應為 64 個字符）
    if [ ${#api_key} -ne 64 ]; then
        log "ERROR" "$service_name API 金鑰長度不正確: ${#api_key} (應為 64)"
        return 1
    fi
    
    # 檢查金鑰格式（應為十六進制）
    if [[ ! "$api_key" =~ ^[a-f0-9]{64}$ ]]; then
        log "ERROR" "$service_name API 金鑰格式不正確（應為 64 位十六進制）"
        return 1
    fi
    
    return 0
}

# 載入單個服務的 API 金鑰
load_service_api_key() {
    local service_name=$1
    local env_var_name=$2
    local api_key_file="$API_KEY_DIR/${service_name}-api-key.txt"
    
    log "INFO" "載入 $service_name 的 API 金鑰..."
    
    # 檢查 API 金鑰檔案是否存在
    if [ ! -f "$api_key_file" ]; then
        log "ERROR" "$service_name API 金鑰檔案不存在: $api_key_file"
        return 1
    fi
    
    # 檢查檔案權限
    local file_perms=$(stat -c "%a" "$api_key_file" 2>/dev/null || echo "unknown")
    if [ "$file_perms" != "600" ]; then
        log "WARN" "$service_name API 金鑰檔案權限不安全: $file_perms (建議 600)"
    fi
    
    # 讀取 API 金鑰
    local api_key=$(cat "$api_key_file" | tr -d '\n\r ')
    
    # 驗證 API 金鑰格式
    if ! validate_api_key "$api_key" "$service_name"; then
        return 1
    fi
    
    # 設定環境變數
    export "$env_var_name"="$api_key"
    
    # 寫入環境檔案
    echo "export $env_var_name=\"$api_key\"" >> "$ENV_FILE"
    
    log "INFO" "$service_name API 金鑰已載入到環境變數 $env_var_name"
    
    # 驗證環境變數是否正確設定
    local loaded_key=$(eval echo \$$env_var_name)
    if [ "$loaded_key" = "$api_key" ]; then
        log "INFO" "$service_name API 金鑰驗證成功"
    else
        log "ERROR" "$service_name API 金鑰載入失敗"
        return 1
    fi
    
    return 0
}

# 載入所有 API 金鑰
load_all_api_keys() {
    log "INFO" "開始載入所有 API 金鑰..."
    
    # 創建環境檔案
    echo "# OpenTelemetry API 金鑰環境變數" > "$ENV_FILE"
    echo "# 生成時間: $(date)" >> "$ENV_FILE"
    echo "" >> "$ENV_FILE"
    
    local success_count=0
    local total_count=${#SERVICES[@]}
    
    # 載入每個服務的 API 金鑰
    for service_name in "${!SERVICES[@]}"; do
        local env_var_name="${SERVICES[$service_name]}"
        
        if load_service_api_key "$service_name" "$env_var_name"; then
            ((success_count++))
        else
            log "ERROR" "載入 $service_name API 金鑰失敗"
        fi
    done
    
    # 設定檔案權限
    chmod 600 "$ENV_FILE"
    
    log "INFO" "API 金鑰載入完成: $success_count/$total_count 成功"
    
    if [ $success_count -eq $total_count ]; then
        log "INFO" "所有 API 金鑰載入成功"
        return 0
    else
        log "ERROR" "部分 API 金鑰載入失敗"
        return 1
    fi
}

# 顯示載入的 API 金鑰狀態
show_api_key_status() {
    log "INFO" "API 金鑰載入狀態:"
    echo "======================================"
    
    for service_name in "${!SERVICES[@]}"; do
        local env_var_name="${SERVICES[$service_name]}"
        local api_key=$(eval echo \$$env_var_name)
        
        if [ -n "$api_key" ]; then
            local masked_key="${api_key:0:8}...${api_key: -8}"
            echo -e "${GREEN}✓ $service_name: $env_var_name = $masked_key${NC}"
        else
            echo -e "${RED}✗ $service_name: $env_var_name = 未設定${NC}"
        fi
    done
    
    echo "======================================"
}

# 驗證所有 API 金鑰
verify_all_api_keys() {
    log "INFO" "驗證所有 API 金鑰..."
    
    local verification_failed=false
    
    for service_name in "${!SERVICES[@]}"; do
        local env_var_name="${SERVICES[$service_name]}"
        local api_key=$(eval echo \$$env_var_name)
        
        if [ -z "$api_key" ]; then
            log "ERROR" "$service_name API 金鑰未載入"
            verification_failed=true
            continue
        fi
        
        if ! validate_api_key "$api_key" "$service_name"; then
            verification_failed=true
            continue
        fi
        
        log "INFO" "$service_name API 金鑰驗證通過"
    done
    
    if [ "$verification_failed" = true ]; then
        log "ERROR" "API 金鑰驗證失敗"
        return 1
    else
        log "INFO" "所有 API 金鑰驗證通過"
        return 0
    fi
}

# 生成 API 金鑰配置檔案供 OpenTelemetry Collector 使用
generate_collector_config() {
    local collector_config_file="/tmp/api-keys-collector.yaml"
    
    log "INFO" "生成 OpenTelemetry Collector API 金鑰配置..."
    
    {
        echo "# OpenTelemetry Collector API 金鑰配置"
        echo "# 生成時間: $(date)"
        echo ""
        echo "# 環境變數配置"
        echo "environment_variables:"
        
        for service_name in "${!SERVICES[@]}"; do
            local env_var_name="${SERVICES[$service_name]}"
            echo "  $env_var_name: \"\${$env_var_name}\""
        done
        
        echo ""
        echo "# API 金鑰標頭配置"
        echo "headers:"
        echo "  mimir:"
        echo "    - \"X-API-Key: \${MIMIR_API_KEY}\""
        echo "    - \"Authorization: Bearer \${MIMIR_API_KEY}\""
        echo "  tempo:"
        echo "    - \"X-API-Key: \${TEMPO_API_KEY}\""
        echo "    - \"Authorization: Bearer \${TEMPO_API_KEY}\""
        echo "  loki:"
        echo "    - \"X-API-Key: \${LOKI_API_KEY}\""
        echo "    - \"Authorization: Bearer \${LOKI_API_KEY}\""
        echo "  grafana:"
        echo "    - \"X-API-Key: \${GRAFANA_API_KEY}\""
        echo "    - \"Authorization: Bearer \${GRAFANA_API_KEY}\""
        
    } > "$collector_config_file"
    
    chmod 600 "$collector_config_file"
    log "INFO" "OpenTelemetry Collector API 金鑰配置已生成: $collector_config_file"
}

# 主函數
main() {
    local action=${1:-load}
    
    case $action in
        load)
            if load_all_api_keys; then
                show_api_key_status
                verify_all_api_keys
                generate_collector_config
                log "INFO" "API 金鑰載入完成"
            else
                log "ERROR" "API 金鑰載入失敗"
                exit 1
            fi
            ;;
        verify)
            verify_all_api_keys
            ;;
        status)
            show_api_key_status
            ;;
        help|*)
            echo "使用方法: $0 [load|verify|status|help]"
            echo "  load   - 載入所有 API 金鑰到環境變數"
            echo "  verify - 驗證所有 API 金鑰"
            echo "  status - 顯示 API 金鑰載入狀態"
            echo "  help   - 顯示此幫助資訊"
            ;;
    esac
}

# 執行主函數
main "$@"