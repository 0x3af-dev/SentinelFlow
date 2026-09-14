package com.sentinelflow.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.dto.InvestigationExplanation;
import com.sentinelflow.ai.validation.ExplanationValidator.AllowedArtifacts;
import com.sentinelflow.ai.validation.ExplanationValidator.ValidationResult;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExplanationValidatorTest {

    private static final String EVIDENCE_A = UUID.randomUUID().toString();
    private static final String EVIDENCE_B = UUID.randomUUID().toString();
    private static final UUID SIM_ID = UUID.randomUUID();
    private static final UUID CF_ID = UUID.randomUUID();

    private AiProperties properties;
    private ExplanationValidator validator;
    private AllowedArtifacts allowed;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        validator = new ExplanationValidator(properties);
        allowed = new AllowedArtifacts(
                Set.of(EVIDENCE_A, EVIDENCE_B),
                Set.of(SIM_ID),
                Set.of(CF_ID),
                "REVIEW", 0.6, "fraud-policy/v1");
    }

    private InvestigationExplanation validAnswer() {
        return new InvestigationExplanation(
                UUID.randomUUID(), "txn-demo-001", "SUMMARIZE",
                "The transaction was scored 0.6 and the persisted decision is REVIEW.",
                List.of(new InvestigationExplanation.Observation("Observed amount spike", List.of(EVIDENCE_A))),
                new InvestigationExplanation.RiskAssessment(
                        0.6, "REVIEW", "fraud-policy/v1",
                        "Persisted policy maps 0.6 to REVIEW.", List.of(EVIDENCE_A)),
                List.of(new InvestigationExplanation.Finding("MODEL_ELEVATED_RISK", List.of(EVIDENCE_B))),
                List.of(new InvestigationExplanation.RuleFinding("RULE-1", "triggered", "REVIEW", List.of(EVIDENCE_A))),
                List.of(new InvestigationExplanation.Finding("behavioral observation", List.of(EVIDENCE_A))),
                List.of(new InvestigationExplanation.EvidenceConflict("model vs rules gap", List.of(EVIDENCE_A, EVIDENCE_B))),
                List.of(new InvestigationExplanation.SimulationExplanation(
                        SIM_ID, "fraud-policy", "sim-v1", "ALLOW",
                        "the simulation indicates ALLOW under threshold 0.75",
                        "Hypothetical; the persisted production decision stays REVIEW.", List.of(EVIDENCE_A))),
                List.of(new InvestigationExplanation.CounterfactualExplanation(
                        CF_ID, "transaction_amount", 12000.0, 60000.0, 0.6, 0.9, "BLOCK",
                        "under the specified hypothetical value, the policy would produce BLOCK",
                        "Hypothetical model output estimated from validated feature edits; no causal claim, no production impact.",
                        List.of(EVIDENCE_B))),
                List.<InvestigationExplanation.Uncertainty>of(),
                List.of(new InvestigationExplanation.RecommendedEvidence("device profile check", "device not seen before")),
                List.of(new InvestigationExplanation.EvidenceReference(EVIDENCE_A, "DECISION", UUID.randomUUID().toString(), "persisted decision")),
                Instant.now(),
                new InvestigationExplanation.ModelMetadata("fake", "gpt-4o-mini", 2, "c1"));
    }

    @Test
    void validAnswerPasses() {
        ValidationResult result = validator.validate(validAnswer(), allowed);
        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void unknownEvidenceIdIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                List.of(new InvestigationExplanation.Observation("made up", List.of(UUID.randomUUID().toString()))),
                answer.riskAssessment(), answer.modelFindings(), answer.ruleFindings(), answer.behavioralFindings(),
                answer.evidenceConflicts(), answer.simulations(), answer.counterfactuals(), answer.uncertainty(),
                answer.recommendedNextEvidence(), answer.evidenceReferences(), answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("unknown evidence id"));
    }

    @Test
    void fabricatedEvidenceReferenceIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                answer.observations(), answer.riskAssessment(), answer.modelFindings(), answer.ruleFindings(),
                answer.behavioralFindings(), answer.evidenceConflicts(), answer.simulations(), answer.counterfactuals(),
                answer.uncertainty(), answer.recommendedNextEvidence(),
                List.of(new InvestigationExplanation.EvidenceReference(
                        UUID.randomUUID().toString(), "DECISION", UUID.randomUUID().toString(), "invented")),
                answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("evidenceReference.evidenceId"));
    }

    @Test
    void mismatchedPersistedDecisionIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                answer.observations(),
                new InvestigationExplanation.RiskAssessment(0.6, "BLOCK", "fraud-policy/v1",
                        answer.riskAssessment().explanation(), answer.riskAssessment().evidenceIds()),
                answer.modelFindings(), answer.ruleFindings(), answer.behavioralFindings(), answer.evidenceConflicts(),
                answer.simulations(), answer.counterfactuals(), answer.uncertainty(), answer.recommendedNextEvidence(),
                answer.evidenceReferences(), answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations())
                .anyMatch(v -> v.contains("recordedDecision") && v.contains("persisted decision"));
    }

    @Test
    void mismatchedRiskScoreIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                answer.observations(),
                new InvestigationExplanation.RiskAssessment(0.9, "REVIEW", "fraud-policy/v1",
                        answer.riskAssessment().explanation(), answer.riskAssessment().evidenceIds()),
                answer.modelFindings(), answer.ruleFindings(), answer.behavioralFindings(), answer.evidenceConflicts(),
                answer.simulations(), answer.counterfactuals(), answer.uncertainty(), answer.recommendedNextEvidence(),
                answer.evidenceReferences(), answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("recordedRiskScore"));
    }

    @Test
    void missingRiskAssessmentEvidenceIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                answer.observations(),
                new InvestigationExplanation.RiskAssessment(0.6, "REVIEW", "fraud-policy/v1",
                        "explanation without citation", List.of()),
                answer.modelFindings(), answer.ruleFindings(), answer.behavioralFindings(), answer.evidenceConflicts(),
                answer.simulations(), answer.counterfactuals(), answer.uncertainty(), answer.recommendedNextEvidence(),
                answer.evidenceReferences(), answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("at least one piece of evidence"));
    }

    @Test
    void unknownSimulationAndCounterfactualIdsAreRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                answer.observations(), answer.riskAssessment(), answer.modelFindings(), answer.ruleFindings(),
                answer.behavioralFindings(), answer.evidenceConflicts(),
                List.of(new InvestigationExplanation.SimulationExplanation(
                        UUID.randomUUID(), "fraud-policy", "vX", "ALLOW", "sim statement",
                        "Hypothetical disclaimer text", List.of(EVIDENCE_A))),
                List.of(new InvestigationExplanation.CounterfactualExplanation(
                        UUID.randomUUID(), "amount", 1.0, 2.0, 0.6, 0.9, "BLOCK", "cf statement",
                        "Hypothetical disclaimer text", List.of(EVIDENCE_A))),
                answer.uncertainty(), answer.recommendedNextEvidence(), answer.evidenceReferences(),
                answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations())
                .anyMatch(v -> v.contains("simulation.simulationId"))
                .anyMatch(v -> v.contains("counterfactual.analysisId"));
    }

    @Test
    void missingHypotheticalDisclaimerIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                answer.observations(), answer.riskAssessment(), answer.modelFindings(), answer.ruleFindings(),
                answer.behavioralFindings(), answer.evidenceConflicts(), answer.simulations(),
                List.of(new InvestigationExplanation.CounterfactualExplanation(
                        CF_ID, "amount", 1.0, 2.0, 0.6, 0.9, "BLOCK", "statement without disclaimer",
                        "  ", List.of(EVIDENCE_A))),
                answer.uncertainty(), answer.recommendedNextEvidence(), answer.evidenceReferences(),
                answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("disclaimer is required"));
    }

    @Test
    void decisionMakingLanguageIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                List.of(new InvestigationExplanation.Observation(
                        "I approve this transaction despite the model score", List.of(EVIDENCE_A))),
                answer.riskAssessment(), answer.modelFindings(), answer.ruleFindings(), answer.behavioralFindings(),
                answer.evidenceConflicts(), answer.simulations(), answer.counterfactuals(), answer.uncertainty(),
                answer.recommendedNextEvidence(), answer.evidenceReferences(), answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("decision-making language"));
    }

    @Test
    void policyMismatchIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                answer.observations(),
                new InvestigationExplanation.RiskAssessment(0.6, "REVIEW", "other-policy/v9",
                        answer.riskAssessment().explanation(), answer.riskAssessment().evidenceIds()),
                answer.modelFindings(), answer.ruleFindings(), answer.behavioralFindings(), answer.evidenceConflicts(),
                answer.simulations(), answer.counterfactuals(), answer.uncertainty(), answer.recommendedNextEvidence(),
                answer.evidenceReferences(), answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("decisionPolicy"));
    }

    @Test
    void policyCitedWhenNoneExistsIsRejected() {
        AllowedArtifacts noPolicy = new AllowedArtifacts(
                Set.of(EVIDENCE_A, EVIDENCE_B), Set.of(SIM_ID), Set.of(CF_ID), "REVIEW", 0.6, null);
        InvestigationExplanation answer = validAnswer();
        ValidationResult result = validator.validate(answer, noPolicy);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("no persisted decision policy exists"));
    }

    @Test
    void referenceCountOverLimitIsRejected() {
        properties.setMaxEvidenceReferences(1);
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), answer.summary(),
                answer.observations(), answer.riskAssessment(), answer.modelFindings(), answer.ruleFindings(),
                answer.behavioralFindings(), answer.evidenceConflicts(), answer.simulations(), answer.counterfactuals(),
                answer.uncertainty(), answer.recommendedNextEvidence(),
                List.of(
                        new InvestigationExplanation.EvidenceReference(EVIDENCE_A, "DECISION", UUID.randomUUID().toString(), "a"),
                        new InvestigationExplanation.EvidenceReference(EVIDENCE_B, "RISK_SCORE", UUID.randomUUID().toString(), "b")),
                answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("exceeds max"));
    }

    @Test
    void blankSummaryIsRejected() {
        InvestigationExplanation answer = validAnswer();
        InvestigationExplanation mutated = new InvestigationExplanation(
                answer.investigationId(), answer.transactionReference(), answer.requestType(), "   ",
                answer.observations(), answer.riskAssessment(), answer.modelFindings(), answer.ruleFindings(),
                answer.behavioralFindings(), answer.evidenceConflicts(), answer.simulations(), answer.counterfactuals(),
                answer.uncertainty(), answer.recommendedNextEvidence(), answer.evidenceReferences(),
                answer.generatedAt(), answer.modelMetadata());

        ValidationResult result = validator.validate(mutated, allowed);
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("summary is required"));
    }
}