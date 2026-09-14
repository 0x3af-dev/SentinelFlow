package com.sentinelflow.analytics.dto;

import java.util.List;
import java.util.UUID;

public record CounterfactualRequest(
        String transactionReference,
        List<CounterfactualFeatureModification> modifications,
        String requestedBy,
        UUID investigationId
) {

    public record CounterfactualFeatureModification(
            String feature,
            Object value
    ) {
    }
}