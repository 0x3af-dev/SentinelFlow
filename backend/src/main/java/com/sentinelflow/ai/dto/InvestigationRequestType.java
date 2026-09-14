package com.sentinelflow.ai.dto;

/**
 * The controlled investigation questions the AI investigator can answer. Each
 * non-free-form type maps to a fixed system-prompt directive; free-form
 * questions are embedded as untrusted user data and still run through the same
 * controlled tool surface.
 */
public enum InvestigationRequestType {
    WHY_FLAGGED,
    SUMMARIZE,
    RISK_FACTORS,
    CONFLICTS,
    BEHAVIORAL,
    NEXT_EVIDENCE,
    FREE_FORM
}