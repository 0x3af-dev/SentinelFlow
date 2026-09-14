package com.sentinelflow.analytics.exception;

public class AnalyticsValidationException extends AnalyticsException {

    public AnalyticsValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}