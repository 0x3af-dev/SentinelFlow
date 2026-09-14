package com.sentinelflow.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CounterfactualResponse(
        UUID analysisId,
        String transactionReference,
        UUID featureSnapshotId,
        String featureSchemaVersion,
        String modelName,
        String modelVersion,
        List<FeatureModificationInfo> appliedModifications,
        double originalRiskScore,
        double hypotheticalRiskScore,
        double scoreDelta,
        String originalDecision,
        String hypotheticalDecision,
        boolean decisionChanged,
        String changeType,
        Instant createdAt,
        String disclaimer
) {

    public record FeatureModificationInfo(
            String feature,
            Object originalValue,
            Object modifiedValue
    ) {
    }

    public static final String DISCLAIMER =
            "Hypothetical model output estimated from validated feature edits; no causal claim, no production impact.";
}