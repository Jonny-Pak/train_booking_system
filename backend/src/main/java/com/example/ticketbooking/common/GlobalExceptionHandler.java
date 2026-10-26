package com.example.ticketbooking.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiError.of(
                exception.getStatus().value(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN", "Access is denied"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uq_active_trip_seat".equals(violation.getConstraintName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiError.of(409, "SEAT_UNAVAILABLE", "One or more seats are no longer available"));
            }
            cause = cause.getCause();
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "DATA_CONFLICT", "Data is duplicated or referenced by another record"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(new ApiError(Instant.now(), status.value(),
                "VALIDATION_ERROR", "Request validation failed", fields), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String code = resolved == null ? "HTTP_ERROR" : resolved.name();
        String message = resolved == null ? "Request failed" : resolved.getReasonPhrase();
        return new ResponseEntity<>(ApiError.of(status.value(), code, message), headers, status);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedException(Exception exception) {
        // Do not log exception messages: downstream exceptions can contain credentials or SQL values.
        log.error("Unhandled request exception: {}", exception.getClass().getName());
        return ResponseEntity.internalServerError().body(ApiError.of(500,
                "INTERNAL_SERVER_ERROR", "An unexpected error occurred"));
    }
}
