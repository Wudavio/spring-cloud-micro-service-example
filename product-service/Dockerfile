FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 複製應用程式 JAR 檔案
COPY target/*.jar app.jar

# 複製 OpenTelemetry Java Agent 和配置檔案（需要在建置時從父目錄複製）
# 這些檔案應該在建置前複製到服務目錄中
COPY opentelemetry-javaagent.jar opentelemetry-javaagent.jar
COPY otel-config.properties otel-config.properties

# 設定 OpenTelemetry 環境變數
ENV OTEL_SERVICE_NAME=product-service
ENV OTEL_SERVICE_VERSION=1.0.0
ENV OTEL_RESOURCE_ATTRIBUTES=deployment.environment=production,service.namespace=microservices,service.instance.id=product-service-1,cluster=microservices-cluster
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4327
ENV OTEL_EXPORTER_OTLP_PROTOCOL=grpc
ENV OTEL_EXPORTER_OTLP_TIMEOUT=30000
ENV OTEL_EXPORTER_OTLP_COMPRESSION=gzip
ENV OTEL_METRICS_EXPORTER=otlp
ENV OTEL_METRIC_EXPORT_INTERVAL=15000
ENV OTEL_METRIC_READERS=periodic
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_TRACES_SAMPLER=traceidratio
ENV OTEL_TRACES_SAMPLER_ARG=1.0
ENV OTEL_LOGS_EXPORTER=otlp
ENV OTEL_JAVAAGENT_CONFIGURATION_FILE=/app/otel-config.properties
ENV OTEL_JAVAAGENT_DEBUG=false
ENV OTEL_INSTRUMENTATION_MICROMETER_ENABLED=true
ENV OTEL_INSTRUMENTATION_SPRING_BOOT_ACTUATOR_AUTOCONFIGURE_ENABLED=true

EXPOSE 8081

# 使用 OpenTelemetry Java Agent 啟動應用程式
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-Deureka.client.serviceUrl.defaultZone=http://eureka-server:8761/eureka/", "-jar", "app.jar"]