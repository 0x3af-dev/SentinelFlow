package com.sentinelflow.ai.exception;

import com.sentinelflow.analytics.exception.AnalyticsException;

/**
 * The LLM produced a response that cannot be returned as evidence-grounded
 * analysis: malformed JSON, unknown/fabricated evidence or artifact references,
 * claims that contradict the persisted decision, prohibited action language, or
 * output that exceeds the configured bounds. The application refuses to return
 * an unverifiable explanation.
 */
public class AiResponseInvalidException extends AnalyticsException {

    public AiResponseInvalidException(String message) {
        super("AI_RESPONSE_INVALID", message);
    }

    public AiResponseInvalidException(String message, Throwable cause) {
        super("AI_RESPONSE_INVALID", message, cause);
    }
}