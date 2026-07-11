package com.microservices.product.exception;

import com.microservices.common.api.ApiErrorResponse;
import com.microservices.common.api.BaseApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(ProductNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DuplicateProductNameException.class)
    ResponseEntity<ApiErrorResponse> duplicate(DuplicateProductNameException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_PRODUCT_NAME", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> details.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> invalidRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request, Map.of());
    }
}
