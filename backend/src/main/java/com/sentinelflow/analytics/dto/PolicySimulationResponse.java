package com.sentinelflow.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record PolicySimulationResponse(
        UUID simulationId,
        String transactionReference,
        String policyName,
        String policyVersion,
        double reviewThreshold,
        double blockThreshold,
        double baseRiskScore,
        String baseModelName,
        String baseModelVersion,
        String actualDecision,
        String actualReason,
        String simulatedDecision,
        String simulatedReason,
        boolean decisionChanged,
        String changeType,
        Instant createdAt
) {
}