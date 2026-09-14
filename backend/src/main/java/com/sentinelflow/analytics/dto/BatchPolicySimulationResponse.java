package com.sentinelflow.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BatchPolicySimulationResponse(
        String policyName,
        String policyVersion,
        int transactionsSimulated,
        int changedCount,
        int unchangedCount,
        Map<String, Integer> outcomeBreakdown,
        List<PolicySimulationResponse> results,
        Instant createdAt
) {
}