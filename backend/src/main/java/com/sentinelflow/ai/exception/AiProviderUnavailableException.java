package com.sentinelflow.ai.exception;

/**
 * Thrown by the AI gateway when the provider is unreachable, times out, is
 * rate-limited, or refuses the request. Mapped to AnalyticsException
 * AI_UNAVAILABLE (HTTP 503) by the API layer.
 */
public class AiProviderUnavailableException extends RuntimeException {

    public AiProviderUnavailableException(String message) {
        super(message);
    }

    public AiProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}