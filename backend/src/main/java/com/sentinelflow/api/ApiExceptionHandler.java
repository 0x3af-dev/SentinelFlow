package com.sentinelflow.api;

import com.sentinelflow.analytics.dto.ApiError;
import com.sentinelflow.analytics.exception.AnalyticsException;
import com.sentinelflow.analytics.exception.AnalyticsMlUnavailableException;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.exception.AnalyticsValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Structured error handling for the analytics API. Responses carry a stable
 * code, a human-readable message, and optional details; stack traces are logged
 * server-side only and never returned to clients.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AnalyticsValidationException.class)
    public ResponseEntity<ApiError> validation(AnalyticsValidationException e) {
        return ResponseEntity.badRequest().body(ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(AnalyticsNotFoundException.class)
    public ResponseEntity<ApiError> notFound(AnalyticsNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(AnalyticsMlUnavailableException.class)
    public ResponseEntity<ApiError> mlUnavailable(AnalyticsMlUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(AnalyticsException.class)
    public ResponseEntity<ApiError> genericAnalytics(AnalyticsException e) {
        log.error("Analytics error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(e.getCode(), "Internal error"));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badArgument(Exception e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_ARGUMENT", "Invalid request parameter or path variable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unexpected analytics API error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "Internal server error"));
    }
}