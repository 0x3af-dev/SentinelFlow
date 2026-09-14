package com.sentinelflow.ai.exception;

import com.sentinelflow.analytics.exception.AnalyticsException;

/**
 * The AI investigation capability is unavailable: the capability is disabled by
 * configuration, the LLM provider is unreachable, timed out, rate limited, or
 * failed, or a requested tool could not be executed. Transaction processing is
 * never affected.
 */
public class AiUnavailableException extends AnalyticsException {

    public AiUnavailableException(String message) {
        super("AI_UNAVAILABLE", message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super("AI_UNAVAILABLE", message, cause);
    }
}