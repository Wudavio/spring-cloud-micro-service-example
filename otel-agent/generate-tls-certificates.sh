#!/bin/bash

# OpenTelemetry TLS 憑證生成腳本
# 為 LGTM 堆疊和 OpenTelemetry Collector 生成 TLS 憑證

set -e

echo "=== OpenTelemetry TLS 憑證生成 ==="
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
CA_KEY="$CERT_DIR/ca-key.pem"
CA_CERT="$CERT_DIR/ca-cert.pem"
VALIDITY_DAYS=365

# 服務列表
SERVICES=(
    "otel-collector"
    "mimir"
    "tempo"
    "loki"
    "grafana"
    "eureka-server"
    "config-server"
    "api-gateway"
    "product-service"
    "inventory-service"
    "order-service"
    "auth-service"
)

# 創建憑證目錄
create_cert_directory() {
    echo -e "${BLUE}創建憑證目錄...${NC}"
    
    if [ ! -d "$CERT_DIR" ]; then
        mkdir -p "$CERT_DIR"
        echo -e "${GREEN}✓ 憑證目錄已創建: $CERT_DIR${NC}"
    else
        echo -e "${YELLOW}⚠ 憑證目錄已存在: $CERT_DIR${NC}"
    fi
}

# 生成 CA 憑證
generate_ca_certificate() {
    echo -e "${BLUE}生成 CA 憑證...${NC}"
    
    if [ ! -f "$CA_KEY" ] || [ ! -f "$CA_CERT" ]; then
        # 生成 CA 私鑰
        openssl genrsa -out "$CA_KEY" 4096
        
        # 生成 CA 憑證
        openssl req -new -x509 -key "$CA_KEY" -sha256 -subj "/C=TW/ST=Taiwan/L=Taipei/O=Microservices/OU=OpenTelemetry/CN=OpenTelemetry-CA" -days $VALIDITY_DAYS -out "$CA_CERT"
        
        echo -e "${GREEN}✓ CA 憑證已生成${NC}"
        echo "  CA 私鑰: $CA_KEY"
        echo "  CA 憑證: $CA_CERT"
    else
        echo -e "${YELLOW}⚠ CA 憑證已存在，跳過生成${NC}"
    fi
}

# 生成服務憑證
generate_service_certificate() {
    local service_name=$1
    local service_key="$CERT_DIR/${service_name}-key.pem"
    local service_csr="$CERT_DIR/${service_name}-csr.pem"
    local service_cert="$CERT_DIR/${service_name}-cert.pem"
    local service_config="$CERT_DIR/${service_name}-cert.conf"
    
    echo -e "${BLUE}生成 $service_name 服務憑證...${NC}"
    
    if [ ! -f "$service_cert" ]; then
        # 創建服務憑證配置檔案
        cat > "$service_config" << EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = v3_req

[dn]
C=TW
ST=Taiwan
L=Taipei
O=Microservices
OU=OpenTelemetry
CN=${service_name}

[v3_req]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = ${service_name}
DNS.2 = localhost
DNS.3 = ${service_name}.local
IP.1 = 127.0.0.1
IP.2 = ::1
EOF

        # 生成服務私鑰
        openssl genrsa -out "$service_key" 2048
        
        # 生成憑證簽名請求
        openssl req -new -key "$service_key" -out "$service_csr" -config "$service_config"
        
        # 使用 CA 簽名生成服務憑證
        openssl x509 -req -in "$service_csr" -CA "$CA_CERT" -CAkey "$CA_KEY" -CAcreateserial -out "$service_cert" -days $VALIDITY_DAYS -extensions v3_req -extfile "$service_config"
        
        # 清理臨時檔案
        rm "$service_csr" "$service_config"
        
        echo -e "${GREEN}✓ $service_name 憑證已生成${NC}"
        echo "  私鑰: $service_key"
        echo "  憑證: $service_cert"
    else
        echo -e "${YELLOW}⚠ $service_name 憑證已存在，跳過生成${NC}"
    fi
}

# 生成 PKCS#12 格式憑證（用於 Java 應用程式）
generate_pkcs12_certificates() {
    echo -e "${BLUE}生成 PKCS#12 格式憑證...${NC}"
    
    for service in "${SERVICES[@]}"; do
        local service_key="$CERT_DIR/${service}-key.pem"
        local service_cert="$CERT_DIR/${service}-cert.pem"
        local service_p12="$CERT_DIR/${service}-keystore.p12"
        
        if [ -f "$service_key" ] && [ -f "$service_cert" ] && [ ! -f "$service_p12" ]; then
            # 生成 PKCS#12 keystore（密碼：changeit）
            openssl pkcs12 -export -in "$service_cert" -inkey "$service_key" -out "$service_p12" -name "$service" -CAfile "$CA_CERT" -caname "OpenTelemetry-CA" -password pass:changeit
            
            echo -e "${GREEN}✓ $service PKCS#12 keystore 已生成: $service_p12${NC}"
        fi
    done
}

# 生成 truststore
generate_truststore() {
    echo -e "${BLUE}生成 truststore...${NC}"
    
    local truststore="$CERT_DIR/truststore.p12"
    
    if [ ! -f "$truststore" ]; then
        # 將 CA 憑證匯入 truststore
        keytool -import -file "$CA_CERT" -alias "OpenTelemetry-CA" -keystore "$truststore" -storepass changeit -storetype PKCS12 -noprompt
        
        echo -e "${GREEN}✓ Truststore 已生成: $truststore${NC}"
    else
        echo -e "${YELLOW}⚠ Truststore 已存在，跳過生成${NC}"
    fi
}

# 設定憑證權限
set_certificate_permissions() {
    echo -e "${BLUE}設定憑證檔案權限...${NC}"
    
    # 設定私鑰權限（僅擁有者可讀）
    find "$CERT_DIR" -name "*-key.pem" -exec chmod 600 {} \;
    
    # 設定憑證權限（擁有者可讀寫，群組和其他人可讀）
    find "$CERT_DIR" -name "*-cert.pem" -exec chmod 644 {} \;
    find "$CERT_DIR" -name "ca-cert.pem" -exec chmod 644 {} \;
    
    # 設定 keystore 權限
    find "$CERT_DIR" -name "*.p12" -exec chmod 600 {} \;
    
    echo -e "${GREEN}✓ 憑證檔案權限已設定${NC}"
}

# 驗證憑證
verify_certificates() {
    echo -e "${BLUE}驗證憑證...${NC}"
    
    # 驗證 CA 憑證
    if openssl x509 -in "$CA_CERT" -text -noout > /dev/null 2>&1; then
        echo -e "${GREEN}✓ CA 憑證有效${NC}"
    else
        echo -e "${RED}✗ CA 憑證無效${NC}"
        return 1
    fi
    
    # 驗證服務憑證
    for service in "${SERVICES[@]}"; do
        local service_cert="$CERT_DIR/${service}-cert.pem"
        
        if [ -f "$service_cert" ]; then
            if openssl verify -CAfile "$CA_CERT" "$service_cert" > /dev/null 2>&1; then
                echo -e "${GREEN}✓ $service 憑證有效${NC}"
            else
                echo -e "${RED}✗ $service 憑證無效${NC}"
                return 1
            fi
        fi
    done
}

# 生成憑證資訊摘要
generate_certificate_summary() {
    echo -e "${BLUE}生成憑證資訊摘要...${NC}"
    
    local summary_file="$CERT_DIR/certificate-summary.txt"
    
    {
        echo "OpenTelemetry TLS 憑證摘要"
        echo "生成時間: $(date)"
        echo "有效期: $VALIDITY_DAYS 天"
        echo "========================================"
        echo
        
        echo "CA 憑證資訊:"
        openssl x509 -in "$CA_CERT" -text -noout | grep -E "(Subject:|Not Before|Not After)"
        echo
        
        echo "服務憑證列表:"
        for service in "${SERVICES[@]}"; do
            local service_cert="$CERT_DIR/${service}-cert.pem"
            if [ -f "$service_cert" ]; then
                echo "- $service"
                openssl x509 -in "$service_cert" -text -noout | grep -E "(Subject:|Not After)" | sed 's/^/  /'
            fi
        done
        echo
        
        echo "憑證檔案列表:"
        ls -la "$CERT_DIR"
        
    } > "$summary_file"
    
    echo -e "${GREEN}✓ 憑證摘要已生成: $summary_file${NC}"
}

# 主函數
main() {
    echo "開始生成 OpenTelemetry TLS 憑證..."
    echo
    
    # 檢查 openssl 是否可用
    if ! command -v openssl &> /dev/null; then
        echo -e "${RED}✗ openssl 未安裝，請先安裝 openssl${NC}"
        exit 1
    fi
    
    # 檢查 keytool 是否可用（用於 Java keystore）
    if ! command -v keytool &> /dev/null; then
        echo -e "${YELLOW}⚠ keytool 未安裝，將跳過 Java keystore 生成${NC}"
    fi
    
    create_cert_directory
    echo
    
    generate_ca_certificate
    echo
    
    # 為每個服務生成憑證
    for service in "${SERVICES[@]}"; do
        generate_service_certificate "$service"
    done
    echo
    
    if command -v keytool &> /dev/null; then
        generate_pkcs12_certificates
        echo
        
        generate_truststore
        echo
    fi
    
    set_certificate_permissions
    echo
    
    verify_certificates
    echo
    
    generate_certificate_summary
    echo
    
    echo -e "${GREEN}✓ 所有 TLS 憑證生成完成！${NC}"
    echo "憑證位置: $CERT_DIR"
    echo "CA 憑證: $CA_CERT"
    echo "使用方法: 請參考各服務的 TLS 配置文件"
    echo "結束時間: $(date)"
}

# 執行主函數
main "$@"