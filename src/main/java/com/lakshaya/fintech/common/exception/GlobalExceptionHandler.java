package com.lakshaya.fintech.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.lakshaya.fintech.common.response.ApiResponse;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // HTTP 403 - ACCESS_DENIED
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("ACCESS_DENIED: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", ex.getMessage()));
    }

    // HTTP 403 - ACCESS_DENIED (Spring Security method-level denial via @PreAuthorize)
    // Fires when a valid token exists but the role is insufficient for the endpoint.
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        log.warn("ACCESS_DENIED (authorization): {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED",
                        "Your role does not have permission to access this endpoint."));
    }

    // HTTP 403 - ACCESS_DENIED (Spring Security filter-level access denial)
    // Fires when the filter chain denies access before reaching the controller.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("ACCESS_DENIED (security): {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED",
                        "Your role does not have permission to access this endpoint."));
    }

    // HTTP 403 - USER_INACTIVE
    @ExceptionHandler(UserInactiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserInactive(UserInactiveException ex) {
        log.warn("USER_INACTIVE: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("USER_INACTIVE", ex.getMessage()));
    }

    // HTTP 404 - NOT_FOUND
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("NOT_FOUND: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", ex.getMessage()));
    }

    // HTTP 409 - INVALID_OPERATION
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOperation(InvalidOperationException ex) {
        log.warn("INVALID_OPERATION: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_OPERATION", ex.getMessage()));
    }

    // HTTP 400 - INVALID_INPUT (manual throw from service)
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(InvalidInputException ex) {
        log.warn("INVALID_INPUT: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_INPUT", ex.getMessage()));
    }

    // HTTP 400 - INVALID_INPUT (from @Valid on request DTOs)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("INVALID_INPUT (validation): {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_INPUT", message));
    }

    // HTTP 400 - INVALID_INPUT (missing/malformed JSON body or invalid enum value)
    // Catches two distinct cases:
    // 1. Body is missing entirely or is not valid JSON
    // 2. A field has an invalid enum value (e.g. "type": "TRANSFER" instead of INCOME/EXPENSE)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("INVALID_INPUT (unreadable body): {}", ex.getMessage());

        String message;
        Throwable cause = ex.getCause();

        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            // Invalid enum value — tell the user exactly which field failed and what values are allowed
            String fieldName = ife.getPath().isEmpty() ? "unknown field"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String invalidValue = String.valueOf(ife.getValue());
            String allowedValues = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            message = "Invalid value '" + invalidValue + "' for field '" + fieldName
                    + "'. Allowed values are: " + allowedValues + ".";
        } else {
            message = "Request body is missing or contains malformed JSON.";
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_INPUT", message));
    }

    // HTTP 500 - catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("INTERNAL_ERROR: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}