package com.sentinelflow.analytics.exception;

public class AnalyticsException extends RuntimeException {

    private final String code;

    public AnalyticsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AnalyticsException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}