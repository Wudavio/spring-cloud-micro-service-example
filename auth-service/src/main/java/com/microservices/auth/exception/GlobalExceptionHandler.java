package com.microservices.auth.exception;

import com.microservices.common.api.ApiErrorResponse;
import com.microservices.common.api.BaseApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> details.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", req, details);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> duplicate(UserAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(UserNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiErrorResponse> unauthorized(BadCredentialsException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password", req, Map.of());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> denied(org.springframework.security.access.AccessDeniedException ex,
                                            HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    ResponseEntity<ApiErrorResponse> tokenRejected(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", ex.getMessage(), req, Map.of());
    }
}
