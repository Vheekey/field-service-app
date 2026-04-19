package com.example.fieldservice.common.errors;

import com.example.fieldservice.common.api.ApiErrorResponse;
import com.example.fieldservice.common.api.ApiErrorResponse.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> authentication(AuthenticationException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed.", request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action.", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = Map.of(
                "fieldErrors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
                        .toList()
        );

        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request, details);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> api(ApiException ex, HttpServletRequest request) {
        return error(ex.status(), ex.code(), ex.getMessage(), request, ex.details());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> optimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Resource was changed by another user.", request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> dataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT", "The requested change conflicts with current data.", request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        String requestId = request.getHeader("X-Request-Id");
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(new ApiError(code, message, requestId, details)));
    }
}
