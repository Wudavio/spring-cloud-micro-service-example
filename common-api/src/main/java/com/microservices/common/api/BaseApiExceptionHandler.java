package com.microservices.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Shared Spring MVC exception → {@link ApiErrorResponse} mapping for all services.
 * Domain handlers stay in each service's GlobalExceptionHandler subclass.
 */
public abstract class BaseApiExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadableBody(HttpMessageNotReadableException ex,
                                                           HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or not valid JSON", req, Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> missingParam(MissingServletRequestParameterException ex,
                                                         HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> missingHeader(MissingRequestHeaderException ex,
                                                          HttpServletRequest req) {
        if ("Authorization".equalsIgnoreCase(ex.getHeaderName())) {
            return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Missing Authorization header", req, Map.of());
        }
        return error(HttpStatus.BAD_REQUEST, "MISSING_HEADER", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex,
                                                         HttpServletRequest req) {
        String name = ex.getName() == null ? "parameter" : ex.getName();
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Invalid value for " + name, req, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest req) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                ex.getMessage() == null ? "HTTP method not supported" : ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unhandled(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {}", req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", req, Map.of());
    }

    protected ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
                                                     HttpServletRequest req, Map<String, String> details) {
        if (status.is5xxServerError()) {
            log.error("{} {} code={}", status.value(), req.getRequestURI(), code);
        } else if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            log.warn("{} {} code={} message={}", status.value(), req.getRequestURI(), code, message);
        }
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), code, message,
                req.getRequestURI(), MDC.get("traceId"), details == null ? Map.of() : details));
    }
}
