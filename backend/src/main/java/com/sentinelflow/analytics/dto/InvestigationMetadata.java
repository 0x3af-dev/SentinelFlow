package com.sentinelflow.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record InvestigationMetadata(
        UUID id,
        String investigationReference,
        String transactionReference,
        String status,
        String priority,
        String assignedTo,
        Instant openedAt,
        Instant updatedAt,
        Instant resolvedAt,
        String resolution,
        String resolutionNotes,
        int eventCount
) {
}