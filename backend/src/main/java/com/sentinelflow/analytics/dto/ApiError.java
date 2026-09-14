package com.sentinelflow.analytics.dto;

import java.util.Map;

/**
 * Structured error envelope returned by the analytics API. Never leaks stack
 * traces; server-side details are logged, not returned.
 */
public record ApiError(String code, String message, Map<String, Object> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }

    public static ApiError of(String code, String message, Map<String, Object> details) {
        return new ApiError(code, message, details);
    }
}