package com.sentinelflow.ai.exception;

/**
 * Thrown by the AI gateway when the provider returned a response that cannot be
 * mapped to a valid structured InvestigationExplanation (malformed JSON, tool
 * budget exceeded, schema violation). Mapped to AnalyticsException
 * AI_RESPONSE_INVALID (HTTP 502) by the API layer.
 */
public class AiProviderResponseException extends RuntimeException {

    public AiProviderResponseException(String message) {
        super(message);
    }

    public AiProviderResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}