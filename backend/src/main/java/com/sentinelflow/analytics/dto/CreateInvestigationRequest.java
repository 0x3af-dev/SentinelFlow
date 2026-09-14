package com.sentinelflow.analytics.dto;

public record CreateInvestigationRequest(
        String transactionReference,
        String priority,
        String assignedTo,
        String note
) {
}