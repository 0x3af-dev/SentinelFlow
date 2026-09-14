package com.sentinelflow.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The structured, evidence-grounded answer produced by the AI investigator.
 * Every factual claim that references SentinelFlow data must carry evidenceIds
 * that resolve to persisted evidence nodes; the application validates this
 * before anything is returned to the frontend. The LLM never decides — the
 * persisted decision remains authoritative and the AI interpretation never
 * modifies it.
 */
public record InvestigationExplanation(
        UUID investigationId,
        String transactionReference,
        String requestType,
        String summary,
        List<Observation> observations,
        RiskAssessment riskAssessment,
        List<Finding> modelFindings,
        List<RuleFinding> ruleFindings,
        List<Finding> behavioralFindings,
        List<EvidenceConflict> evidenceConflicts,
        List<SimulationExplanation> simulations,
        List<CounterfactualExplanation> counterfactuals,
        List<Uncertainty> uncertainty,
        List<RecommendedEvidence> recommendedNextEvidence,
        List<EvidenceReference> evidenceReferences,
        Instant generatedAt,
        ModelMetadata modelMetadata
) {

    public record Observation(String statement, List<String> evidenceIds) {
    }

    public record RiskAssessment(
            double recordedRiskScore,
            String recordedDecision,
            String decisionPolicy,
            String explanation,
            List<String> evidenceIds
    ) {
    }

    /** A model-or-behavioral finding: a plain statement plus its evidence. */
    public record Finding(String statement, List<String> evidenceIds) {
    }

    public record RuleFinding(
            String ruleId,
            String statement,
            String outcome,
            List<String> evidenceIds
    ) {
    }

    public record EvidenceConflict(String description, List<String> evidenceIds) {
    }

    public record CounterfactualExplanation(
            UUID analysisId,
            String feature,
            Object recordedValue,
            Object hypotheticalValue,
            double recordedScore,
            double hypotheticalScore,
            String hypotheticalDecision,
            String statement,
            String disclaimer,
            List<String> evidenceIds
    ) {
    }

    public record SimulationExplanation(
            UUID simulationId,
            String hypotheticalPolicyName,
            String hypotheticalPolicyVersion,
            String simulatedDecision,
            String statement,
            String disclaimer,
            List<String> evidenceIds
    ) {
    }

    public record Uncertainty(String statement, String reason) {
    }

    public record RecommendedEvidence(String request, String rationale) {
    }

    public record EvidenceReference(
            String evidenceId,
            String sourceType,
            String sourceId,
            String description
    ) {
    }

    public record ModelMetadata(
            String provider,
            String model,
            int toolCallCount,
            String correlationId
    ) {
    }
}