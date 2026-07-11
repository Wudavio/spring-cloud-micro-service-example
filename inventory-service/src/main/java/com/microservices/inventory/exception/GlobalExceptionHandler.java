package com.microservices.inventory.exception;

import com.microservices.common.api.ApiErrorResponse;
import com.microservices.inventory.service.InventoryService;
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

    @ExceptionHandler(InventoryService.InsufficientStockException.class)
    ResponseEntity<ApiErrorResponse> insufficient(InventoryService.InsufficientStockException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    ResponseEntity<ApiErrorResponse> inventoryNotFound(InventoryNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(InventoryService.ReservationNotFoundException.class)
    ResponseEntity<ApiErrorResponse> reservationNotFound(InventoryService.ReservationNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(InventoryService.ConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> concurrent(InventoryService.ConcurrentModificationException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Please retry the request", req, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> details.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", req, details);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiErrorResponse> wrapped(RuntimeException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof InventoryService.InsufficientStockException cause) return insufficient(cause, req);
        if (ex.getCause() instanceof InventoryService.ReservationNotFoundException cause) return reservationNotFound(cause, req);
        if (ex.getCause() instanceof InventoryService.ConcurrentModificationException cause) return concurrent(cause, req);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", req, Map.of());
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
