package com.microservices.order.exception;

import com.microservices.common.api.ApiErrorResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiErrorResponse> orderNotFound(OrderNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(CartNotFoundException.class)
    ResponseEntity<ApiErrorResponse> cartNotFound(CartNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "CART_NOT_FOUND", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ApiErrorResponse> productNotFound(ProductNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    ResponseEntity<ApiErrorResponse> insufficient(InsufficientInventoryException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "INSUFFICIENT_INVENTORY", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResponse> invalidState(IllegalStateException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS_TRANSITION", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> details.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", req, details);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> denied(org.springframework.security.access.AccessDeniedException ex,
                                            HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    ResponseEntity<ApiErrorResponse> unauthenticated(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(FeignException.class)
    ResponseEntity<ApiErrorResponse> downstream(FeignException ex, HttpServletRequest req) {
        HttpStatus status = ex.status() == 404 ? HttpStatus.NOT_FOUND
                : ex.status() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return error(status, "DOWNSTREAM_SERVICE_ERROR", "Downstream service request failed", req, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> general(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", req, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
                                                    HttpServletRequest req, Map<String, String> details) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), code, message,
                req.getRequestURI(), MDC.get("traceId"), details));
    }
}
