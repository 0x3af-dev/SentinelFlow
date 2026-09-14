package com.sentinelflow.analytics.dto;

import java.util.List;

public record BatchPolicySimulationRequest(
        List<String> transactionReferences,
        String policyName,
        String policyVersion,
        Double reviewThreshold,
        Double blockThreshold,
        String requestedBy
) {
}