#!/bin/bash

# 敏感資料過濾測試腳本
# 用於驗證敏感資料檢測和脫敏功能

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DATA_DIR="${SCRIPT_DIR}/test-data"
FILTER_SCRIPT="${SCRIPT_DIR}/data-privacy-filter.sh"

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

# 創建測試資料目錄
setup_test_data() {
    log_info "設定測試資料..."
    
    mkdir -p "$TEST_DATA_DIR"
    
    # 創建包含敏感資料的測試指標
    cat > "${TEST_DATA_DIR}/test-metrics.json" << 'EOF'
{
  "resourceMetrics": [
    {
      "resource": {
        "attributes": [
          {"key": "service.name", "value": {"stringValue": "test-service"}},
          {"key": "password", "value": {"stringValue": "secret123"}},
          {"key": "api_key", "value": {"stringValue": "ak_1234567890abcdef"}},
          {"key": "db.connection_string", "value": {"stringValue": "jdbc:mysql://localhost:3306/test?user=admin&password=secret123"}}
        ]
      },
      "scopeMetrics": [
        {
          "metrics": [
            {
              "name": "http_requests_total",
              "description": "Total HTTP requests",
              "unit": "1",
              "sum": {
                "dataPoints": [
                  {
                    "attributes": [
                      {"key": "method", "value": {"stringValue": "GET"}},
                      {"key": "url", "value": {"stringValue": "https://api.example.com/users?token=secret123&password=admin123"}},
                      {"key": "authorization", "value": {"stringValue": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"}}
                    ],
                    "value": 42
                  }
                ]
              }
            }
          ]
        }
      ]
    }
  ]
}
EOF

    # 創建包含敏感資料的測試追蹤
    cat > "${TEST_DATA_DIR}/test-traces.json" << 'EOF'
{
  "resourceSpans": [
    {
      "resource": {
        "attributes": [
          {"key": "service.name", "value": {"stringValue": "auth-service"}},
          {"key": "client_secret", "value": {"stringValue": "cs_1234567890abcdef"}}
        ]
      },
      "scopeSpans": [
        {
          "spans": [
            {
              "traceId": "1234567890abcdef1234567890abcdef",
              "spanId": "abcdef1234567890",
              "name": "POST /login",
              "kind": 3,
              "startTimeUnixNano": 1640995200000000000,
              "endTimeUnixNano": 1640995201000000000,
              "attributes": [
                {"key": "http.method", "value": {"stringValue": "POST"}},
                {"key": "http.url", "value": {"stringValue": "https://auth.example.com/login?redirect_uri=https://app.example.com&client_secret=secret123"}},
                {"key": "http.request.header.authorization", "value": {"stringValue": "Basic YWRtaW46cGFzc3dvcmQxMjM="}},
                {"key": "http.request.header.cookie", "value": {"stringValue": "session_id=abc123; auth_token=xyz789"}},
                {"key": "db.statement", "value": {"stringValue": "SELECT * FROM users WHERE username = 'admin' AND password = 'secret123'"}},
                {"key": "user.password", "value": {"stringValue": "plaintext_password_123"}}
              ],
              "events": [
                {
                  "timeUnixNano": 1640995200500000000,
                  "name": "User authentication",
                  "attributes": [
                    {"key": "user.id", "value": {"stringValue": "user123"}},
                    {"key": "password_hash", "value": {"stringValue": "$2b$12$abcdef1234567890"}}
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
EOF

    # 創建包含敏感資料的測試日誌
    cat > "${TEST_DATA_DIR}/test-logs.json" << 'EOF'
{
  "resourceLogs": [
    {
      "resource": {
        "attributes": [
          {"key": "service.name", "value": {"stringValue": "payment-service"}},
          {"key": "private_key", "value": {"stringValue": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC..."}}
        ]
      },
      "scopeLogs": [
        {
          "logRecords": [
            {
              "timeUnixNano": 1640995200000000000,
              "severityNumber": 9,
              "severityText": "INFO",
              "body": {
                "stringValue": "Processing payment for user john.doe@example.com with credit card 4532-1234-5678-9012 and CVV 123. API key: ak_1234567890abcdef, password: secret123"
              },
              "attributes": [
                {"key": "user.email", "value": {"stringValue": "john.doe@example.com"}},
                {"key": "credit_card", "value": {"stringValue": "4532-1234-5678-9012"}},
                {"key": "api_key", "value": {"stringValue": "ak_1234567890abcdef"}},
                {"key": "db_password", "value": {"stringValue": "db_secret_123"}},
                {"key": "bearer_token", "value": {"stringValue": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"}}
              ]
            },
            {
              "timeUnixNano": 1640995201000000000,
              "severityNumber": 13,
              "severityText": "ERROR",
              "body": {
                "stringValue": "Database connection failed: jdbc:postgresql://db.example.com:5432/payments?user=admin&password=db_secret_456&sslmode=require"
              },
              "attributes": [
                {"key": "error.type", "value": {"stringValue": "DatabaseConnectionError"}},
                {"key": "connection_string", "value": {"stringValue": "postgresql://admin:db_secret_456@db.example.com:5432/payments"}}
              ]
            }
          ]
        }
      ]
    }
  ]
}
EOF

    log_success "測試資料創建完成"
}

# 測試指標過濾
test_metrics_filtering() {
    log_info "測試指標資料過濾..."
    
    local input_file="${TEST_DATA_DIR}/test-metrics.json"
    local output_file="${TEST_DATA_DIR}/filtered-metrics.json"
    
    # 執行過濾
    if "$FILTER_SCRIPT" process metrics "$input_file" "$output_file"; then
        log_success "指標過濾執行成功"
        
        # 驗證敏感資料是否被移除
        if grep -q "secret123" "$output_file"; then
            log_error "指標過濾失敗：仍包含敏感資料 'secret123'"
            return 1
        fi
        
        if grep -q "ak_1234567890abcdef" "$output_file"; then
            log_error "指標過濾失敗：仍包含敏感資料 'ak_1234567890abcdef'"
            return 1
        fi
        
        if grep -q "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" "$output_file"; then
            log_error "指標過濾失敗：仍包含敏感資料 'Bearer token'"
            return 1
        fi
        
        # 驗證正常資料是否保留
        if ! grep -q "service.name" "$output_file"; then
            log_error "指標過濾失敗：正常資料被錯誤移除"
            return 1
        fi
        
        if ! grep -q "http_requests_total" "$output_file"; then
            log_error "指標過濾失敗：指標名稱被錯誤移除"
            return 1
        fi
        
        log_success "指標過濾驗證通過"
    else
        log_error "指標過濾執行失敗"
        return 1
    fi
}

# 測試追蹤過濾
test_traces_filtering() {
    log_info "測試追蹤資料過濾..."
    
    local input_file="${TEST_DATA_DIR}/test-traces.json"
    local output_file="${TEST_DATA_DIR}/filtered-traces.json"
    
    # 執行過濾
    if "$FILTER_SCRIPT" process traces "$input_file" "$output_file"; then
        log_success "追蹤過濾執行成功"
        
        # 驗證敏感資料是否被移除
        if grep -q "cs_1234567890abcdef" "$output_file"; then
            log_error "追蹤過濾失敗：仍包含敏感資料 'client_secret'"
            return 1
        fi
        
        if grep -q "Basic YWRtaW46cGFzc3dvcmQxMjM=" "$output_file"; then
            log_error "追蹤過濾失敗：仍包含敏感資料 'Basic auth'"
            return 1
        fi
        
        if grep -q "plaintext_password_123" "$output_file"; then
            log_error "追蹤過濾失敗：仍包含敏感資料 'plaintext_password'"
            return 1
        fi
        
        # 驗證正常資料是否保留
        if ! grep -q "auth-service" "$output_file"; then
            log_error "追蹤過濾失敗：服務名稱被錯誤移除"
            return 1
        fi
        
        if ! grep -q "POST /login" "$output_file"; then
            log_error "追蹤過濾失敗：span 名稱被錯誤移除"
            return 1
        fi
        
        log_success "追蹤過濾驗證通過"
    else
        log_error "追蹤過濾執行失敗"
        return 1
    fi
}

# 測試日誌過濾
test_logs_filtering() {
    log_info "測試日誌資料過濾..."
    
    local input_file="${TEST_DATA_DIR}/test-logs.json"
    local output_file="${TEST_DATA_DIR}/filtered-logs.json"
    
    # 執行過濾
    if "$FILTER_SCRIPT" process logs "$input_file" "$output_file"; then
        log_success "日誌過濾執行成功"
        
        # 驗證敏感資料是否被移除
        if grep -q "4532-1234-5678-9012" "$output_file"; then
            log_error "日誌過濾失敗：仍包含敏感資料 '信用卡號'"
            return 1
        fi
        
        if grep -q "john.doe@example.com" "$output_file"; then
            log_error "日誌過濾失敗：仍包含敏感資料 '電子郵件'"
            return 1
        fi
        
        if grep -q "db_secret_123" "$output_file"; then
            log_error "日誌過濾失敗：仍包含敏感資料 'db_password'"
            return 1
        fi
        
        if grep -q "-----BEGIN PRIVATE KEY-----" "$output_file"; then
            log_error "日誌過濾失敗：仍包含敏感資料 'private_key'"
            return 1
        fi
        
        # 驗證正常資料是否保留
        if ! grep -q "payment-service" "$output_file"; then
            log_error "日誌過濾失敗：服務名稱被錯誤移除"
            return 1
        fi
        
        if ! grep -q "INFO" "$output_file"; then
            log_error "日誌過濾失敗：日誌級別被錯誤移除"
            return 1
        fi
        
        log_success "日誌過濾驗證通過"
    else
        log_error "日誌過濾執行失敗"
        return 1
    fi
}

# 測試合規性報告生成
test_compliance_report() {
    log_info "測試合規性報告生成..."
    
    if "$FILTER_SCRIPT" report; then
        log_success "合規性報告生成成功"
        
        # 檢查報告檔案是否存在
        local report_files
        report_files=$(find "$SCRIPT_DIR" -name "compliance-report-*.json" -mtime -1)
        
        if [ -z "$report_files" ]; then
            log_error "合規性報告檔案未找到"
            return 1
        fi
        
        local latest_report
        latest_report=$(echo "$report_files" | head -1)
        
        # 驗證報告內容
        if ! jq -e '.compliance_summary.compliance_score' "$latest_report" > /dev/null; then
            log_error "合規性報告格式錯誤：缺少合規性分數"
            return 1
        fi
        
        local compliance_score
        compliance_score=$(jq -r '.compliance_summary.compliance_score' "$latest_report")
        
        log_info "合規性分數: $compliance_score%"
        
        if [ $(echo "$compliance_score >= 90" | bc) -eq 1 ]; then
            log_success "合規性分數達標: $compliance_score%"
        else
            log_warn "合規性分數偏低: $compliance_score%"
        fi
        
        log_success "合規性報告驗證通過"
    else
        log_error "合規性報告生成失敗"
        return 1
    fi
}

# 清理測試資料
cleanup_test_data() {
    log_info "清理測試資料..."
    
    if [ -d "$TEST_DATA_DIR" ]; then
        rm -rf "$TEST_DATA_DIR"
        log_success "測試資料清理完成"
    fi
    
    # 清理生成的報告檔案
    find "$SCRIPT_DIR" -name "compliance-report-*.json" -mtime -1 -delete 2>/dev/null || true
    find "$SCRIPT_DIR" -name "data-privacy-*.log" -delete 2>/dev/null || true
    find "$SCRIPT_DIR" -name "data-privacy-stats.json" -delete 2>/dev/null || true
}

# 主測試函數
run_tests() {
    log_info "開始敏感資料過濾測試..."
    
    local test_results=()
    
    # 初始化過濾器
    if "$FILTER_SCRIPT" init; then
        log_success "過濾器初始化成功"
    else
        log_error "過濾器初始化失敗"
        return 1
    fi
    
    # 設定測試資料
    setup_test_data
    
    # 執行各項測試
    if test_metrics_filtering; then
        test_results+=("指標過濾: 通過")
    else
        test_results+=("指標過濾: 失敗")
    fi
    
    if test_traces_filtering; then
        test_results+=("追蹤過濾: 通過")
    else
        test_results+=("追蹤過濾: 失敗")
    fi
    
    if test_logs_filtering; then
        test_results+=("日誌過濾: 通過")
    else
        test_results+=("日誌過濾: 失敗")
    fi
    
    if test_compliance_report; then
        test_results+=("合規性報告: 通過")
    else
        test_results+=("合規性報告: 失敗")
    fi
    
    # 顯示測試結果
    log_info "測試結果摘要:"
    for result in "${test_results[@]}"; do
        if [[ "$result" == *"通過"* ]]; then
            log_success "  $result"
        else
            log_error "  $result"
        fi
    done
    
    # 清理測試資料
    cleanup_test_data
    
    # 檢查是否所有測試都通過
    local failed_tests
    failed_tests=$(printf '%s\n' "${test_results[@]}" | grep -c "失敗" || true)
    
    if [ "$failed_tests" -eq 0 ]; then
        log_success "所有敏感資料過濾測試通過！"
        return 0
    else
        log_error "$failed_tests 個測試失敗"
        return 1
    fi
}

# 執行測試
if [ "${1:-}" = "run" ]; then
    run_tests
else
    echo "敏感資料過濾測試腳本"
    echo "使用方式: $0 run"
fi