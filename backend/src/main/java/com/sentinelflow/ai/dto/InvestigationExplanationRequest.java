package com.sentinelflow.ai.dto;

import com.sentinelflow.analytics.exception.AnalyticsValidationException;

/**
 * Request body for {@code POST /api/investigations/{id}/explanations}. The
 * question is either one of the controlled types (WHY_FLAGGED, SUMMARIZE,
 * RISK_FACTORS, CONFLICTS, BEHAVIORAL, NEXT_EVIDENCE) or a bounded free-form
 * question. The operation invokes an external model, so it is a POST, matching
 * the repository's create/generate semantics (policy lab, counterfactuals).
 */
public record InvestigationExplanationRequest(
        InvestigationRequestType requestType,
        String freeFormQuestion
) {

    public static final int MAX_FREE_FORM_LENGTH = 500;

    public InvestigationExplanationRequest {
        if (requestType == null) {
            throw new AnalyticsValidationException("requestType is required");
        }
        if (requestType == InvestigationRequestType.FREE_FORM) {
            if (freeFormQuestion == null || freeFormQuestion.isBlank()) {
                throw new AnalyticsValidationException("freeFormQuestion is required for FREE_FORM");
            }
            if (freeFormQuestion.trim().length() > MAX_FREE_FORM_LENGTH) {
                throw new AnalyticsValidationException(
                        "freeFormQuestion exceeds " + MAX_FREE_FORM_LENGTH + " characters");
            }
        } else if (freeFormQuestion != null && !freeFormQuestion.isBlank()) {
            throw new AnalyticsValidationException(
                    "freeFormQuestion is only allowed for FREE_FORM requests");
        }
    }

    public String freeFormQuestion() {
        return freeFormQuestion == null ? null : freeFormQuestion.trim();
    }
}