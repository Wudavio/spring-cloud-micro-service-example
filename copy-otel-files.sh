#!/bin/bash

# 複製 OpenTelemetry 檔案到各個微服務目錄的腳本

SERVICES=("product-service" "order-service" "inventory-service" "auth-service" "api-gateway" "eureka-server" "config-server")

echo "開始複製 OpenTelemetry 檔案到各個微服務目錄..."

for service in "${SERVICES[@]}"; do
    echo "複製檔案到 $service..."
    
    # 複製 OpenTelemetry Java Agent JAR
    cp otel-agent/opentelemetry-javaagent.jar "$service/"
    
    # 複製配置檔案
    cp otel-agent/otel-config.properties "$service/"
    
    echo "✓ $service 完成"
done

echo "所有檔案複製完成！"
echo ""
echo "現在可以建置 Docker 映像："
echo "docker-compose build"