package com.sentinelflow.analytics.exception;

public class AnalyticsMlUnavailableException extends AnalyticsException {

    public AnalyticsMlUnavailableException(String message) {
        super("ML_UNAVAILABLE", message);
    }

    public AnalyticsMlUnavailableException(String message, Throwable cause) {
        super("ML_UNAVAILABLE", message, cause);
    }
}