package com.sentinelflow.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Complete read-only reconstruction of the decision lineage for one
 * transaction. Assembled exclusively from persisted Phase 1/2 data; no ML
 * inference is performed and nothing is mutated.
 */
public record DecisionReplayResponse(
        String transactionReference,
        TransactionInfo transaction,
        FeatureSnapshotInfo featureSnapshot,
        ModelInfo model,
        RiskScoreInfo riskScore,
        List<RiskFactorInfo> riskFactors,
        List<RuleInfo> triggeredRules,
        PolicyInfo policy,
        DecisionInfo decision,
        DisagreementInfo disagreement,
        EvidenceInfo evidence
) {

    public record TransactionInfo(
            String status,
            BigDecimal amount,
            String currency,
            String channel,
            String transactionType,
            Instant timestamp
    ) {
    }

    public record FeatureSnapshotInfo(
            UUID id,
            String schemaVersion,
            Instant generatedAt,
            Map<String, Object> features
    ) {
    }

    public record ModelInfo(
            String name,
            String version,
            String algorithm,
            String featureSchemaVersion
    ) {
    }

    public record RiskScoreInfo(
            double score,
            String prediction,
            Instant inferenceTimestamp,
            Integer inferenceLatencyMs
    ) {
    }

    public record RiskFactorInfo(
            String factorType,
            String description,
            String severity,
            String source
    ) {
    }

    public record RuleInfo(
            String ruleId,
            String ruleVersion,
            String severity,
            String description,
            Map<String, Object> observedValues
    ) {
    }

    public record PolicyInfo(
            String name,
            String version,
            Double reviewThreshold,
            Double blockThreshold,
            Map<String, Object> configuration
    ) {
    }

    public record DecisionInfo(
            String finalDecision,
            String reason,
            Instant decisionTimestamp
    ) {
    }
}