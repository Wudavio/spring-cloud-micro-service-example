#!/bin/bash

# OpenTelemetry API 金鑰管理腳本
# 用於生成、管理和輪換 API 金鑰

set -e

echo "=== OpenTelemetry API 金鑰管理 ==="
echo "開始時間: $(date)"
echo

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
API_KEY_DIR="../api-keys"
BACKUP_DIR="../api-keys/backup"
CONFIG_FILE="../otel-agent/api-key-config.yaml"
LOG_FILE="/tmp/api-key-management.log"

# 服務列表
SERVICES=(
    "otel-collector"
    "mimir"
    "tempo"
    "loki"
    "grafana"
)

# 使用方法
usage() {
    echo "使用方法: $0 [選項]"
    echo "選項:"
    echo "  generate [service]    生成 API 金鑰（可指定服務）"
    echo "  rotate [service]      輪換 API 金鑰（可指定服務）"
    echo "  list                  列出所有 API 金鑰"
    echo "  verify [service]      驗證 API 金鑰（可指定服務）"
    echo "  backup                備份 API 金鑰"
    echo "  restore               從備份恢復 API 金鑰"
    echo "  revoke [service]      撤銷 API 金鑰"
    echo "  cleanup               清理過期的 API 金鑰"
    echo "  help                  顯示此幫助資訊"
    echo
    echo "範例:"
    echo "  $0 generate           # 生成所有服務的 API 金鑰"
    echo "  $0 generate mimir     # 只生成 Mimir 的 API 金鑰"
    echo "  $0 rotate             # 輪換所有即將過期的 API 金鑰"
    echo "  $0 list               # 列出所有 API 金鑰"
}

# 記錄函數
log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] [$level] $message" | tee -a "$LOG_FILE"
}

# 生成隨機 API 金鑰
generate_api_key() {
    local service_name=$1
    local key_length=${2:-64}
    
    # 生成隨機字串作為 API 金鑰
    local api_key=$(openssl rand -hex $((key_length / 2)))
    echo "$api_key"
}

# 創建 API 金鑰目錄
create_api_key_directory() {
    log "INFO" "創建 API 金鑰目錄..."
    
    if [ ! -d "$API_KEY_DIR" ]; then
        mkdir -p "$API_KEY_DIR"
        log "INFO" "API 金鑰目錄已創建: $API_KEY_DIR"
    fi
    
    if [ ! -d "$BACKUP_DIR" ]; then
        mkdir -p "$BACKUP_DIR"
        log "INFO" "備份目錄已創建: $BACKUP_DIR"
    fi
}

# 生成服務 API 金鑰
generate_service_api_key() {
    local service_name=$1
    local api_key_file="$API_KEY_DIR/${service_name}-api-key.txt"
    local api_key_info_file="$API_KEY_DIR/${service_name}-api-key-info.json"
    
    log "INFO" "生成 $service_name 的 API 金鑰..."
    
    # 生成 API 金鑰
    local api_key=$(generate_api_key "$service_name")
    local created_at=$(date -Iseconds)
    local expires_at=$(date -d "+90 days" -Iseconds)
    local key_id=$(uuidgen 2>/dev/null || echo "$(date +%s)-$(shuf -i 1000-9999 -n 1)")
    
    # 保存 API 金鑰
    echo "$api_key" > "$api_key_file"
    chmod 600 "$api_key_file"
    
    # 保存 API 金鑰資訊
    cat > "$api_key_info_file" << EOF
{
  "service": "$service_name",
  "key_id": "$key_id",
  "created_at": "$created_at",
  "expires_at": "$expires_at",
  "status": "active",
  "key_file": "$api_key_file",
  "permissions": [
    "telemetry.write",
    "telemetry.read"
  ],
  "rate_limit": {
    "requests_per_minute": 1000,
    "burst_size": 100
  }
}
EOF
    chmod 600 "$api_key_info_file"
    
    log "INFO" "$service_name API 金鑰已生成"
    log "INFO" "  金鑰 ID: $key_id"
    log "INFO" "  過期時間: $expires_at"
    log "INFO" "  金鑰檔案: $api_key_file"
    
    echo "$api_key"
}

# 生成所有服務的 API 金鑰
generate_all_api_keys() {
    log "INFO" "生成所有服務的 API 金鑰..."
    
    create_api_key_directory
    
    for service in "${SERVICES[@]}"; do
        generate_service_api_key "$service"
    done
    
    # 生成主配置檔案
    generate_api_key_config
    
    log "INFO" "所有 API 金鑰生成完成"
}

# 生成 API 金鑰配置檔案
generate_api_key_config() {
    log "INFO" "生成 API 金鑰配置檔案..."
    
    local config_file="$API_KEY_DIR/api-keys-config.yaml"
    
    {
        echo "# OpenTelemetry API 金鑰配置"
        echo "# 生成時間: $(date)"
        echo ""
        echo "api_keys:"
        
        for service in "${SERVICES[@]}"; do
            local api_key_info_file="$API_KEY_DIR/${service}-api-key-info.json"
            
            if [ -f "$api_key_info_file" ]; then
                local key_id=$(jq -r '.key_id' "$api_key_info_file" 2>/dev/null || echo "unknown")
                local expires_at=$(jq -r '.expires_at' "$api_key_info_file" 2>/dev/null || echo "unknown")
                
                echo "  $service:"
                echo "    key_id: \"$key_id\""
                echo "    key_file: \"$API_KEY_DIR/${service}-api-key.txt\""
                echo "    expires_at: \"$expires_at\""
                echo "    status: \"active\""
                echo ""
            fi
        done
        
        echo "# 金鑰輪換策略"
        echo "rotation_policy:"
        echo "  enabled: true"
        echo "  rotation_interval: \"90d\""
        echo "  warning_threshold: \"7d\""
        echo "  auto_rotate: false"
        echo ""
        
        echo "# 安全策略"
        echo "security_policy:"
        echo "  key_length: 64"
        echo "  encryption: \"AES-256\""
        echo "  hash_algorithm: \"SHA-256\""
        echo "  rate_limiting: true"
        
    } > "$config_file"
    
    chmod 600 "$config_file"
    log "INFO" "API 金鑰配置檔案已生成: $config_file"
}

# 列出所有 API 金鑰
list_api_keys() {
    log "INFO" "列出所有 API 金鑰..."
    
    echo -e "${BLUE}API 金鑰列表:${NC}"
    echo "======================================"
    
    for service in "${SERVICES[@]}"; do
        local api_key_info_file="$API_KEY_DIR/${service}-api-key-info.json"
        
        if [ -f "$api_key_info_file" ]; then
            local key_id=$(jq -r '.key_id' "$api_key_info_file" 2>/dev/null || echo "unknown")
            local created_at=$(jq -r '.created_at' "$api_key_info_file" 2>/dev/null || echo "unknown")
            local expires_at=$(jq -r '.expires_at' "$api_key_info_file" 2>/dev/null || echo "unknown")
            local status=$(jq -r '.status' "$api_key_info_file" 2>/dev/null || echo "unknown")
            
            echo -e "${GREEN}服務: $service${NC}"
            echo "  金鑰 ID: $key_id"
            echo "  狀態: $status"
            echo "  創建時間: $created_at"
            echo "  過期時間: $expires_at"
            
            # 檢查是否即將過期
            local expires_timestamp=$(date -d "$expires_at" +%s 2>/dev/null || echo "0")
            local current_timestamp=$(date +%s)
            local warning_timestamp=$((current_timestamp + 7 * 24 * 3600))
            
            if [ $expires_timestamp -lt $warning_timestamp ] && [ $expires_timestamp -gt $current_timestamp ]; then
                echo -e "  ${YELLOW}⚠ 即將過期${NC}"
            elif [ $expires_timestamp -lt $current_timestamp ]; then
                echo -e "  ${RED}✗ 已過期${NC}"
            else
                echo -e "  ${GREEN}✓ 有效${NC}"
            fi
            
            echo ""
        else
            echo -e "${YELLOW}服務: $service - 無 API 金鑰${NC}"
        fi
    done
}

# 驗證 API 金鑰
verify_api_key() {
    local service_name=$1
    local api_key_file="$API_KEY_DIR/${service_name}-api-key.txt"
    local api_key_info_file="$API_KEY_DIR/${service_name}-api-key-info.json"
    
    log "INFO" "驗證 $service_name 的 API 金鑰..."
    
    if [ ! -f "$api_key_file" ] || [ ! -f "$api_key_info_file" ]; then
        log "ERROR" "$service_name 的 API 金鑰檔案不存在"
        return 1
    fi
    
    # 檢查檔案權限
    local key_perms=$(stat -c "%a" "$api_key_file" 2>/dev/null || stat -f "%A" "$api_key_file" 2>/dev/null || echo "unknown")
    if [ "$key_perms" != "600" ]; then
        log "WARN" "$service_name API 金鑰檔案權限不正確: $key_perms (應為 600)"
    fi
    
    # 檢查金鑰格式
    local api_key=$(cat "$api_key_file")
    if [[ ! "$api_key" =~ ^[a-f0-9]{64}$ ]]; then
        log "ERROR" "$service_name API 金鑰格式不正確"
        return 1
    fi
    
    # 檢查過期時間
    local expires_at=$(jq -r '.expires_at' "$api_key_info_file" 2>/dev/null || echo "unknown")
    local expires_timestamp=$(date -d "$expires_at" +%s 2>/dev/null || echo "0")
    local current_timestamp=$(date +%s)
    
    if [ $expires_timestamp -lt $current_timestamp ]; then
        log "ERROR" "$service_name API 金鑰已過期: $expires_at"
        return 1
    fi
    
    log "INFO" "$service_name API 金鑰驗證通過"
    return 0
}

# 輪換 API 金鑰
rotate_api_key() {
    local service_name=$1
    local api_key_info_file="$API_KEY_DIR/${service_name}-api-key-info.json"
    
    log "INFO" "輪換 $service_name 的 API 金鑰..."
    
    if [ -f "$api_key_info_file" ]; then
        # 備份舊金鑰
        local backup_timestamp=$(date '+%Y%m%d-%H%M%S')
        local backup_path="$BACKUP_DIR/${service_name}-${backup_timestamp}"
        mkdir -p "$backup_path"
        
        cp "$API_KEY_DIR/${service_name}-"* "$backup_path/" 2>/dev/null || true
        
        # 標記舊金鑰為已撤銷
        jq '.status = "revoked" | .revoked_at = "'$(date -Iseconds)'"' "$api_key_info_file" > "${api_key_info_file}.tmp"
        mv "${api_key_info_file}.tmp" "$backup_path/$(basename "$api_key_info_file")"
        
        log "INFO" "舊 API 金鑰已備份到: $backup_path"
    fi
    
    # 生成新金鑰
    generate_service_api_key "$service_name"
    
    log "INFO" "$service_name API 金鑰輪換完成"
}

# 輪換所有即將過期的 API 金鑰
rotate_expiring_keys() {
    log "INFO" "檢查需要輪換的 API 金鑰..."
    
    local rotation_needed=false
    local warning_days=7
    
    for service in "${SERVICES[@]}"; do
        local api_key_info_file="$API_KEY_DIR/${service}-api-key-info.json"
        
        if [ -f "$api_key_info_file" ]; then
            local expires_at=$(jq -r '.expires_at' "$api_key_info_file" 2>/dev/null || echo "unknown")
            local expires_timestamp=$(date -d "$expires_at" +%s 2>/dev/null || echo "0")
            local current_timestamp=$(date +%s)
            local warning_timestamp=$((current_timestamp + warning_days * 24 * 3600))
            
            if [ $expires_timestamp -lt $warning_timestamp ]; then
                log "WARN" "$service API 金鑰需要輪換（過期時間: $expires_at）"
                rotate_api_key "$service"
                rotation_needed=true
            fi
        fi
    done
    
    if [ "$rotation_needed" = false ]; then
        log "INFO" "所有 API 金鑰都在有效期內，無需輪換"
    else
        # 重新生成配置檔案
        generate_api_key_config
        log "INFO" "API 金鑰輪換完成"
    fi
}

# 撤銷 API 金鑰
revoke_api_key() {
    local service_name=$1
    local api_key_info_file="$API_KEY_DIR/${service_name}-api-key-info.json"
    
    log "INFO" "撤銷 $service_name 的 API 金鑰..."
    
    if [ -f "$api_key_info_file" ]; then
        # 更新狀態為已撤銷
        jq '.status = "revoked" | .revoked_at = "'$(date -Iseconds)'"' "$api_key_info_file" > "${api_key_info_file}.tmp"
        mv "${api_key_info_file}.tmp" "$api_key_info_file"
        
        log "INFO" "$service_name API 金鑰已撤銷"
    else
        log "ERROR" "$service_name API 金鑰不存在"
        return 1
    fi
}

# 備份 API 金鑰
backup_api_keys() {
    log "INFO" "備份 API 金鑰..."
    
    local backup_timestamp=$(date '+%Y%m%d-%H%M%S')
    local backup_path="$BACKUP_DIR/full-backup-$backup_timestamp"
    
    mkdir -p "$backup_path"
    
    if [ -d "$API_KEY_DIR" ]; then
        cp -r "$API_KEY_DIR"/* "$backup_path/" 2>/dev/null || true
        
        # 創建備份資訊
        {
            echo "API 金鑰完整備份"
            echo "備份時間: $(date)"
            echo "備份路徑: $backup_path"
            echo "備份內容:"
            ls -la "$backup_path"
        } > "$backup_path/backup-info.txt"
        
        log "INFO" "API 金鑰備份完成: $backup_path"
    else
        log "ERROR" "API 金鑰目錄不存在: $API_KEY_DIR"
        return 1
    fi
}

# 清理過期的備份和撤銷的金鑰
cleanup_expired_keys() {
    log "INFO" "清理過期的 API 金鑰和備份..."
    
    local retention_days=30
    
    # 清理過期備份
    if [ -d "$BACKUP_DIR" ]; then
        find "$BACKUP_DIR" -type d -name "*-[0-9]*" -mtime +$retention_days -exec rm -rf {} \; 2>/dev/null || true
        log "INFO" "已清理超過 $retention_days 天的備份"
    fi
    
    # 清理已撤銷的金鑰資訊（保留備份）
    for service in "${SERVICES[@]}"; do
        local api_key_info_file="$API_KEY_DIR/${service}-api-key-info.json"
        
        if [ -f "$api_key_info_file" ]; then
            local status=$(jq -r '.status' "$api_key_info_file" 2>/dev/null || echo "unknown")
            local revoked_at=$(jq -r '.revoked_at' "$api_key_info_file" 2>/dev/null || echo "null")
            
            if [ "$status" = "revoked" ] && [ "$revoked_at" != "null" ]; then
                local revoked_timestamp=$(date -d "$revoked_at" +%s 2>/dev/null || echo "0")
                local current_timestamp=$(date +%s)
                local cleanup_timestamp=$((current_timestamp - retention_days * 24 * 3600))
                
                if [ $revoked_timestamp -lt $cleanup_timestamp ]; then
                    log "INFO" "清理已撤銷的 $service API 金鑰"
                    rm -f "$API_KEY_DIR/${service}-"*
                fi
            fi
        fi
    done
}

# 主函數
main() {
    local action=${1:-help}
    local service_name=$2
    
    case $action in
        generate)
            if [ -n "$service_name" ]; then
                create_api_key_directory
                generate_service_api_key "$service_name"
                generate_api_key_config
            else
                generate_all_api_keys
            fi
            ;;
        rotate)
            if [ -n "$service_name" ]; then
                rotate_api_key "$service_name"
                generate_api_key_config
            else
                rotate_expiring_keys
            fi
            ;;
        list)
            list_api_keys
            ;;
        verify)
            if [ -n "$service_name" ]; then
                verify_api_key "$service_name"
            else
                for service in "${SERVICES[@]}"; do
                    verify_api_key "$service" || true
                done
            fi
            ;;
        revoke)
            if [ -n "$service_name" ]; then
                revoke_api_key "$service_name"
            else
                echo "請指定要撤銷的服務名稱"
                exit 1
            fi
            ;;
        backup)
            backup_api_keys
            ;;
        cleanup)
            cleanup_expired_keys
            ;;
        help|*)
            usage
            ;;
    esac
}

# 執行主函數
main "$@"