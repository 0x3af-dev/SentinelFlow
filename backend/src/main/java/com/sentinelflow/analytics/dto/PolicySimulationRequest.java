package com.sentinelflow.analytics.dto;

import java.util.UUID;

public record PolicySimulationRequest(
        String transactionReference,
        String policyName,
        String policyVersion,
        Double reviewThreshold,
        Double blockThreshold,
        String requestedBy,
        UUID investigationId
) {
}