#!/bin/bash

# OpenTelemetry TLS 憑證管理腳本
# 用於管理憑證的生成、更新、驗證和輪換

set -e

echo "=== OpenTelemetry TLS 憑證管理 ==="
echo "開始時間: $(date)"
echo

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
CERT_DIR="../certs"
BACKUP_DIR="../certs/backup"
CONFIG_FILE="../otel-agent/tls-config.yaml"
LOG_FILE="/tmp/tls-cert-management.log"

# 使用方法
usage() {
    echo "使用方法: $0 [選項]"
    echo "選項:"
    echo "  generate    生成所有 TLS 憑證"
    echo "  verify      驗證所有憑證"
    echo "  rotate      輪換即將過期的憑證"
    echo "  backup      備份現有憑證"
    echo "  restore     從備份恢復憑證"
    echo "  monitor     監控憑證狀態"
    echo "  cleanup     清理過期的備份檔案"
    echo "  help        顯示此幫助資訊"
    echo
    echo "範例:"
    echo "  $0 generate   # 生成所有憑證"
    echo "  $0 verify     # 驗證所有憑證"
    echo "  $0 rotate     # 輪換即將過期的憑證"
}

# 記錄函數
log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] [$level] $message" | tee -a "$LOG_FILE"
}

# 檢查憑證是否即將過期
check_certificate_expiry() {
    local cert_file=$1
    local warning_days=${2:-30}
    
    if [ ! -f "$cert_file" ]; then
        return 1
    fi
    
    local expiry_date=$(openssl x509 -in "$cert_file" -noout -enddate | cut -d= -f2)
    local expiry_timestamp=$(date -d "$expiry_date" +%s)
    local current_timestamp=$(date +%s)
    local warning_timestamp=$((current_timestamp + warning_days * 24 * 3600))
    
    if [ $expiry_timestamp -lt $warning_timestamp ]; then
        return 0  # 即將過期
    else
        return 1  # 未過期
    fi
}

# 生成憑證
generate_certificates() {
    log "INFO" "開始生成 TLS 憑證..."
    
    if [ -f "../otel-agent/generate-tls-certificates.sh" ]; then
        bash "../otel-agent/generate-tls-certificates.sh"
        log "INFO" "憑證生成完成"
    else
        log "ERROR" "憑證生成腳本不存在"
        return 1
    fi
}

# 驗證憑證
verify_certificates() {
    log "INFO" "開始驗證 TLS 憑證..."
    
    local ca_cert="$CERT_DIR/ca-cert.pem"
    local error_count=0
    
    if [ ! -f "$ca_cert" ]; then
        log "ERROR" "CA 憑證不存在: $ca_cert"
        return 1
    fi
    
    # 驗證 CA 憑證
    if openssl x509 -in "$ca_cert" -text -noout > /dev/null 2>&1; then
        log "INFO" "CA 憑證驗證通過"
    else
        log "ERROR" "CA 憑證驗證失敗"
        ((error_count++))
    fi
    
    # 驗證服務憑證
    for cert_file in "$CERT_DIR"/*-cert.pem; do
        if [ -f "$cert_file" ]; then
            local service_name=$(basename "$cert_file" -cert.pem)
            
            if openssl verify -CAfile "$ca_cert" "$cert_file" > /dev/null 2>&1; then
                log "INFO" "$service_name 憑證驗證通過"
                
                # 檢查憑證過期時間
                local expiry_date=$(openssl x509 -in "$cert_file" -noout -enddate | cut -d= -f2)
                log "INFO" "$service_name 憑證過期時間: $expiry_date"
                
                # 檢查是否即將過期
                if check_certificate_expiry "$cert_file" 30; then
                    log "WARN" "$service_name 憑證將在 30 天內過期"
                fi
            else
                log "ERROR" "$service_name 憑證驗證失敗"
                ((error_count++))
            fi
        fi
    done
    
    if [ $error_count -eq 0 ]; then
        log "INFO" "所有憑證驗證通過"
        return 0
    else
        log "ERROR" "發現 $error_count 個憑證錯誤"
        return 1
    fi
}

# 備份憑證
backup_certificates() {
    log "INFO" "開始備份 TLS 憑證..."
    
    local backup_timestamp=$(date '+%Y%m%d-%H%M%S')
    local backup_path="$BACKUP_DIR/$backup_timestamp"
    
    mkdir -p "$backup_path"
    
    if [ -d "$CERT_DIR" ]; then
        cp -r "$CERT_DIR"/* "$backup_path/" 2>/dev/null || true
        log "INFO" "憑證備份完成: $backup_path"
        
        # 創建備份資訊檔案
        {
            echo "備份時間: $(date)"
            echo "備份路徑: $backup_path"
            echo "備份內容:"
            ls -la "$backup_path"
        } > "$backup_path/backup-info.txt"
        
        return 0
    else
        log "ERROR" "憑證目錄不存在: $CERT_DIR"
        return 1
    fi
}

# 從備份恢復憑證
restore_certificates() {
    log "INFO" "開始從備份恢復 TLS 憑證..."
    
    if [ ! -d "$BACKUP_DIR" ]; then
        log "ERROR" "備份目錄不存在: $BACKUP_DIR"
        return 1
    fi
    
    # 列出可用的備份
    echo "可用的備份:"
    ls -1 "$BACKUP_DIR" | grep -E '^[0-9]{8}-[0-9]{6}$' | sort -r | head -10
    
    echo -n "請輸入要恢復的備份時間戳 (格式: YYYYMMDD-HHMMSS): "
    read backup_timestamp
    
    local backup_path="$BACKUP_DIR/$backup_timestamp"
    
    if [ ! -d "$backup_path" ]; then
        log "ERROR" "指定的備份不存在: $backup_path"
        return 1
    fi
    
    # 備份當前憑證
    backup_certificates
    
    # 恢復憑證
    rm -rf "$CERT_DIR"
    mkdir -p "$CERT_DIR"
    cp -r "$backup_path"/* "$CERT_DIR/" 2>/dev/null || true
    
    log "INFO" "憑證恢復完成: $backup_path"
    
    # 驗證恢復的憑證
    verify_certificates
}

# 輪換憑證
rotate_certificates() {
    log "INFO" "開始檢查需要輪換的憑證..."
    
    local rotation_needed=false
    local warning_days=30
    
    # 檢查哪些憑證需要輪換
    for cert_file in "$CERT_DIR"/*-cert.pem; do
        if [ -f "$cert_file" ]; then
            local service_name=$(basename "$cert_file" -cert.pem)
            
            if check_certificate_expiry "$cert_file" $warning_days; then
                log "WARN" "$service_name 憑證需要輪換"
                rotation_needed=true
            fi
        fi
    done
    
    if [ "$rotation_needed" = true ]; then
        log "INFO" "發現需要輪換的憑證，開始輪換流程..."
        
        # 備份現有憑證
        backup_certificates
        
        # 重新生成憑證
        generate_certificates
        
        # 驗證新憑證
        verify_certificates
        
        log "INFO" "憑證輪換完成"
    else
        log "INFO" "所有憑證都在有效期內，無需輪換"
    fi
}

# 監控憑證狀態
monitor_certificates() {
    log "INFO" "開始監控 TLS 憑證狀態..."
    
    local status_file="/tmp/tls-cert-status.json"
    
    {
        echo "{"
        echo "  \"timestamp\": \"$(date -Iseconds)\","
        echo "  \"certificates\": ["
        
        local first=true
        for cert_file in "$CERT_DIR"/*-cert.pem; do
            if [ -f "$cert_file" ]; then
                local service_name=$(basename "$cert_file" -cert.pem)
                
                if [ "$first" = false ]; then
                    echo ","
                fi
                first=false
                
                local expiry_date=$(openssl x509 -in "$cert_file" -noout -enddate | cut -d= -f2)
                local expiry_timestamp=$(date -d "$expiry_date" +%s)
                local current_timestamp=$(date +%s)
                local days_until_expiry=$(( (expiry_timestamp - current_timestamp) / 86400 ))
                
                local status="valid"
                if [ $days_until_expiry -lt 0 ]; then
                    status="expired"
                elif [ $days_until_expiry -lt 30 ]; then
                    status="expiring_soon"
                fi
                
                echo -n "    {"
                echo -n "\"service\": \"$service_name\", "
                echo -n "\"expiry_date\": \"$expiry_date\", "
                echo -n "\"days_until_expiry\": $days_until_expiry, "
                echo -n "\"status\": \"$status\""
                echo -n "}"
            fi
        done
        
        echo ""
        echo "  ]"
        echo "}"
    } > "$status_file"
    
    log "INFO" "憑證狀態已更新: $status_file"
    
    # 顯示摘要
    echo -e "${BLUE}憑證狀態摘要:${NC}"
    cat "$status_file" | jq -r '.certificates[] | "\(.service): \(.status) (過期: \(.days_until_expiry) 天)"' 2>/dev/null || cat "$status_file"
}

# 清理過期備份
cleanup_backups() {
    log "INFO" "開始清理過期的備份檔案..."
    
    local retention_days=90
    
    if [ -d "$BACKUP_DIR" ]; then
        find "$BACKUP_DIR" -type d -name "[0-9]*-[0-9]*" -mtime +$retention_days -exec rm -rf {} \; 2>/dev/null || true
        log "INFO" "已清理超過 $retention_days 天的備份檔案"
    fi
}

# 主函數
main() {
    local action=${1:-help}
    
    case $action in
        generate)
            generate_certificates
            ;;
        verify)
            verify_certificates
            ;;
        rotate)
            rotate_certificates
            ;;
        backup)
            backup_certificates
            ;;
        restore)
            restore_certificates
            ;;
        monitor)
            monitor_certificates
            ;;
        cleanup)
            cleanup_backups
            ;;
        help|*)
            usage
            ;;
    esac
}

# 執行主函數
main "$@"