package com.sentinelflow.analytics.exception;

public class AnalyticsNotFoundException extends AnalyticsException {

    public AnalyticsNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}