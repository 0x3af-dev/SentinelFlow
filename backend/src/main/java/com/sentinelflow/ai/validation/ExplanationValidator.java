package com.sentinelflow.ai.validation;

import com.sentinelflow.ai.config.AiProperties;
import com.sentinelflow.ai.dto.InvestigationExplanation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates the model's structured answer BEFORE it leaves the application.
 * The model output is always treated as untrusted. Rules enforced here:
 *
 * <ul>
 *   <li>every cited evidence id must exist in the evidence shown to the model</li>
 *   <li>the stated production decision and risk score must exactly match the
 *       persisted record</li>
 *   <li>the stated decision policy must match the persisted policy when one
 *       exists, and must be omitted when none exists</li>
 *   <li>simulation/counterfactual artifact ids must refer to recorded analyses</li>
 *   <li>no decision-making, policy-changing or override language is allowed</li>
 *   <li>outputs stay within configured bounds</li>
 * </ul>
 *
 * A failed validation discards the output entirely; it is never shown to a
 * user and never persisted as a response.
 */
@Component
public class ExplanationValidator {

    public record AllowedArtifacts(
            Set<String> evidenceNodeIds,
            Set<UUID> simulationIds,
            Set<UUID> counterfactualAnalysisIds,
            String persistedDecision,
            double persistedRiskScore,
            String persistedPolicyReference
    ) {
    }

    public record ValidationResult(boolean valid, List<String> violations) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(List<String> violations) {
            return new ValidationResult(false, List.copyOf(violations));
        }
    }

    private static final List<String> BANNED_ACTION_PHRASES = List.of(
            "i approve",
            "i have approved",
            "i approved",
            "i decided to",
            "i would approve",
            "i would change",
            "i changed the decision",
            "i overrode",
            "i have overridden",
            "set the policy",
            "i will increase",
            "i will decrease",
            "approve this transaction",
            "block this transaction",
            "override the production decision",
            "i recommend approving",
            "i recommend blocking"
    );

    private final AiProperties properties;

    public ExplanationValidator(AiProperties properties) {
        this.properties = properties;
    }

    public ValidationResult validate(InvestigationExplanation answer, AllowedArtifacts allowed) {
        List<String> violations = new ArrayList<>();

        if (blank(answer.summary())) {
            violations.add("summary is required and must be non-blank");
        }
        requireEvidenceIds("riskAssessment.evidenceIds", answer.riskAssessment().evidenceIds(), allowed, violations);

        validateRiskAssessment(answer.riskAssessment(), allowed, violations);

        for (var observation : answer.observations()) {
            requireEvidenceIds("observation", observation.evidenceIds(), allowed, violations);
            checkBanned(observation.statement(), violations);
        }
        for (var finding : answer.modelFindings()) {
            requireEvidenceIds("modelFinding", finding.evidenceIds(), allowed, violations);
            checkBanned(finding.statement(), violations);
        }
        for (var finding : answer.behavioralFindings()) {
            requireEvidenceIds("behavioralFinding", finding.evidenceIds(), allowed, violations);
            checkBanned(finding.statement(), violations);
        }
        for (var rule : answer.ruleFindings()) {
            requireEvidenceIds("ruleFinding " + rule.ruleId(), rule.evidenceIds(), allowed, violations);
            checkBanned(rule.statement(), violations);
        }
        for (var conflict : answer.evidenceConflicts()) {
            requireEvidenceIds("evidenceConflict", conflict.evidenceIds(), allowed, violations);
            checkBanned(conflict.description(), violations);
        }

        validateSimulations(answer.simulations(), allowed, violations);
        validateCounterfactuals(answer.counterfactuals(), allowed, violations);
        validateReferences(answer.evidenceReferences(), allowed, violations);

        if (answer.recommendedNextEvidence().size() > properties.getMaxRecommendedEvidence()) {
            violations.add("recommendedNextEvidence exceeds max "
                    + properties.getMaxRecommendedEvidence());
        }
        if (answer.observations().size() > properties.getMaxObservations()) {
            violations.add("observations exceeds max " + properties.getMaxObservations());
        }

        return violations.isEmpty() ? ValidationResult.ok()
                : ValidationResult.invalid(violations);
    }

    private void validateRiskAssessment(InvestigationExplanation.RiskAssessment riskAssessment,
                                        AllowedArtifacts allowed, List<String> violations) {
        if (blank(riskAssessment.explanation())) {
            violations.add("riskAssessment.explanation is required and must be non-blank");
        }
        if (riskAssessment.evidenceIds().isEmpty()) {
            violations.add("riskAssessment must cite at least one piece of evidence");
        }
        String decision = riskAssessment.recordedDecision();
        if (!equalsIgnoreCaseTrim(decision, allowed.persistedDecision())) {
            violations.add("riskAssessment.recordedDecision '"
                    + decision + "' does not match persisted decision '"
                    + allowed.persistedDecision() + "'");
        }
        if (Math.abs(riskAssessment.recordedRiskScore() - allowed.persistedRiskScore()) > 1e-9) {
            violations.add("riskAssessment.recordedRiskScore '"
                    + riskAssessment.recordedRiskScore() + "' does not match persisted score '"
                    + allowed.persistedRiskScore() + "'");
        }
        String policy = riskAssessment.decisionPolicy();
        if (blank(allowed.persistedPolicyReference())) {
            if (!blank(policy)) {
                violations.add("riskAssessment.decisionPolicy '" + policy
                        + "' provided but no persisted decision policy exists");
            }
        } else if (!equalsIgnoreCaseTrim(policy, allowed.persistedPolicyReference())) {
            violations.add("riskAssessment.decisionPolicy '" + policy
                    + "' does not match persisted policy reference '"
                    + allowed.persistedPolicyReference() + "'");
        }
        checkBanned(riskAssessment.explanation(), violations);
    }

    private void validateSimulations(List<InvestigationExplanation.SimulationExplanation> simulations,
                                     AllowedArtifacts allowed, List<String> violations) {
        for (var simulation : simulations) {
            if (simulation.simulationId() == null || !allowed.simulationIds().contains(simulation.simulationId())) {
                violations.add("simulation.simulationId '" + simulation.simulationId()
                        + "' does not refer to a recorded policy simulation");
            }
            if (blank(simulation.disclaimer())) {
                violations.add("simulation disclaimer is required");
            }
            checkBanned(simulation.statement(), violations);
        }
    }

    private void validateCounterfactuals(List<InvestigationExplanation.CounterfactualExplanation> counterfactuals,
                                         AllowedArtifacts allowed, List<String> violations) {
        for (var counterfactual : counterfactuals) {
            if (counterfactual.analysisId() == null
                    || !allowed.counterfactualAnalysisIds().contains(counterfactual.analysisId())) {
                violations.add("counterfactual.analysisId '" + counterfactual.analysisId()
                        + "' does not refer to a recorded counterfactual analysis");
            }
            if (blank(counterfactual.disclaimer())) {
                violations.add("counterfactual disclaimer is required");
            }
            checkBanned(counterfactual.statement(), violations);
        }
    }

    private void validateReferences(List<InvestigationExplanation.EvidenceReference> references,
                                    AllowedArtifacts allowed, List<String> violations) {
        if (references.size() > properties.getMaxEvidenceReferences()) {
            violations.add("evidenceReferences exceeds max " + properties.getMaxEvidenceReferences());
        }
        for (var reference : references) {
            if (!containsIgnoreCase(allowed.evidenceNodeIds(), reference.evidenceId())) {
                violations.add("evidenceReference.evidenceId '" + reference.evidenceId()
                        + "' does not match any evidence node id shown to the model");
            }
            if (blank(reference.sourceId()) || blank(reference.sourceType())) {
                violations.add("evidenceReference must carry sourceType and sourceId");
            }
        }
        Set<String> referenced = new HashSet<>();
        for (var reference : references) {
            if (!referenced.add(reference.evidenceId().toLowerCase(Locale.ROOT))) {
                violations.add("duplicate evidenceReference '" + reference.evidenceId() + "'");
            }
        }
    }

    private void requireEvidenceIds(String path, List<String> evidenceIds,
                                    AllowedArtifacts allowed, List<String> violations) {
        for (String cited : evidenceIds) {
            if (!containsIgnoreCase(allowed.evidenceNodeIds(), cited)) {
                violations.add(path + " cites unknown evidence id '" + cited + "'");
            }
        }
    }

    private void checkBanned(String text, List<String> violations) {
        if (text == null) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String phrase : BANNED_ACTION_PHRASES) {
            if (lower.contains(phrase)) {
                violations.add("output contains decision-making language: \"" + phrase + "\"");
            }
        }
    }

    private static boolean containsIgnoreCase(Set<String> values, String candidate) {
        if (candidate == null) {
            return false;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).equals(lower)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsIgnoreCaseTrim(String left, String right) {
        return left != null && right != null
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}