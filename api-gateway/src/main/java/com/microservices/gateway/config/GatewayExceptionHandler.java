package com.microservices.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.common.api.ApiErrorResponse;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        // Only normalize known gateway routing / status failures; let other errors fall through.
        if (!(exception instanceof NotFoundException) && !(exception instanceof ResponseStatusException)) {
            return Mono.error(exception);
        }

        HttpStatus status = resolveStatus(exception);
        String code = status == HttpStatus.NOT_FOUND ? "ROUTE_NOT_FOUND" : "GATEWAY_ERROR";
        String message = status == HttpStatus.NOT_FOUND
                ? "No route is available for this request"
                : (exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "Gateway request failed" : exception.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of(status.value(), code, message,
                exchange.getRequest().getPath().value(), MDC.get("traceId"), Map.of());
        try {
            byte[] body = objectMapper.writeValueAsBytes(error);
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception serializationFailure) {
            return Mono.error(serializationFailure);
        }
    }

    private HttpStatus resolveStatus(Throwable exception) {
        if (exception instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof ResponseStatusException responseStatus) {
            HttpStatus resolved = HttpStatus.resolve(responseStatus.getStatusCode().value());
            return resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
