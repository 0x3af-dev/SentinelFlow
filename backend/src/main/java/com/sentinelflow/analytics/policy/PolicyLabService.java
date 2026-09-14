package com.sentinelflow.analytics.policy;

import com.sentinelflow.analytics.dto.BatchPolicySimulationRequest;
import com.sentinelflow.analytics.dto.BatchPolicySimulationResponse;
import com.sentinelflow.analytics.dto.PolicySimulationRequest;
import com.sentinelflow.analytics.dto.PolicySimulationResponse;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.exception.AnalyticsValidationException;
import com.sentinelflow.analytics.model.PolicySimulation;
import com.sentinelflow.analytics.model.PolicySimulationRepository;
import com.sentinelflow.analytics.shared.ThresholdDecision;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.investigation.service.InvestigationEventPublisher;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Policy Lab. Simulates the impact of proposed thresholds over risk scores that
 * were already produced by the production pipeline. It never invokes the model,
 * never mutates the production policy, and never alters the decision it
 * re-evaluates.
 */
@Service
public class PolicyLabService {

    public static final String DEFAULT_POLICY_NAME = "fraud-policy";

    private final TransactionRepository transactionRepository;
    private final DecisionRecordRepository decisionRepository;
    private final PolicySimulationRepository simulationRepository;
    private final InvestigationEventPublisher investigationEvents;

    public PolicyLabService(TransactionRepository transactionRepository,
                            DecisionRecordRepository decisionRepository,
                            PolicySimulationRepository simulationRepository,
                            InvestigationEventPublisher investigationEvents) {
        this.transactionRepository = transactionRepository;
        this.decisionRepository = decisionRepository;
        this.simulationRepository = simulationRepository;
        this.investigationEvents = investigationEvents;
    }

    @Transactional
    public PolicySimulationResponse simulate(PolicySimulationRequest request) {
        String reference = requiredText(request.transactionReference(), "transactionReference");
        String policyName = request.policyName() == null || request.policyName().isBlank()
                ? DEFAULT_POLICY_NAME : request.policyName();
        String policyVersion = requiredText(request.policyVersion(), "policyVersion");
        double review = requiredThreshold(request.reviewThreshold(), "reviewThreshold");
        double block = requiredThreshold(request.blockThreshold(), "blockThreshold");
        ThresholdDecision.validate(review, block);

        DecisionRecord decision = loadBaseDecision(reference);
        RiskScore riskScore = decision.getRiskScore();
        double score = riskScore.getRiskScore();
        String actual = decision.getFinalDecision().name();
        String simulated = ThresholdDecision.evaluate(score, review, block);
        boolean changed = !actual.equals(simulated);
        String changeType = ThresholdDecision.changeType(actual, simulated);
        String explanation = String.format(
                "Policy Lab [%s v%s]: base risk score %.2f evaluates to %s under proposed "
                        + "review=%.2f / block=%.2f (actual production decision: %s).",
                policyName, policyVersion, score, simulated, review, block, actual);

        PolicySimulation saved = simulationRepository.save(new PolicySimulation(
                reference, decision.getId(), policyName, policyVersion, review, block, score,
                actual, simulated, changed, changeType, explanation, request.requestedBy()));

        if (request.investigationId() != null) {
            investigationEvents.record(request.investigationId(), reference,
                    "POLICY_SIMULATION_EXECUTED", "ANALYST", request.requestedBy(), Map.of(
                            "simulationId", saved.getId().toString(),
                            "actualDecision", actual,
                            "simulatedDecision", simulated,
                            "decisionChanged", changed));
        }

        ModelVersion model = riskScore.getModelVersion();
        return new PolicySimulationResponse(
                saved.getId(), reference, policyName, policyVersion, review, block, score,
                model.getModelName(), model.getVersion(),
                actual, decision.getDecisionReason(), simulated,
                ThresholdDecision.reason(score, review, block, simulated),
                changed, changeType, saved.getCreatedAt());
    }

    @Transactional
    public BatchPolicySimulationResponse simulateBatch(BatchPolicySimulationRequest request) {
        if (request.transactionReferences() == null || request.transactionReferences().isEmpty()) {
            throw new AnalyticsValidationException("transactionReferences must not be empty");
        }
        double review = requiredThreshold(request.reviewThreshold(), "reviewThreshold");
        double block = requiredThreshold(request.blockThreshold(), "blockThreshold");
        ThresholdDecision.validate(review, block);

        List<String> references = request.transactionReferences().stream().distinct().toList();
        String policyName = request.policyName() == null || request.policyName().isBlank()
                ? DEFAULT_POLICY_NAME : request.policyName();
        String policyVersion = requiredText(request.policyVersion(), "policyVersion");

        List<PolicySimulationResponse> results = references.stream()
                .map(ref -> simulate(new PolicySimulationRequest(
                        ref, policyName, policyVersion, review, block, request.requestedBy(), null)))
                .toList();

        long changed = results.stream().filter(PolicySimulationResponse::decisionChanged).count();
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        for (PolicySimulationResponse r : results) {
            String key = r.actualDecision() + "->" + r.simulatedDecision();
            breakdown.merge(key, 1, Integer::sum);
        }

        return new BatchPolicySimulationResponse(
                policyName, policyVersion, results.size(), (int) changed,
                results.size() - (int) changed, breakdown, results, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PolicySimulationResponse> listSimulations(String transactionReference) {
        String reference = requiredText(transactionReference, "transactionReference");
        List<PolicySimulation> rows = simulationRepository
                .findByTransactionReferenceOrderByCreatedAtDesc(reference);
        if (rows.isEmpty()) {
            return List.of();
        }
        DecisionRecord decision = loadBaseDecision(reference);
        ModelVersion model = decision.getRiskScore().getModelVersion();
        String modelName = model.getModelName();
        String modelVersion = model.getVersion();

        return rows.stream().map(s -> new PolicySimulationResponse(
                s.getId(), s.getTransactionReference(), s.getPolicyName(), s.getPolicyVersion(),
                s.getReviewThreshold(), s.getBlockThreshold(), s.getBaseRiskScore(),
                modelName, modelVersion,
                s.getActualDecision(), reasonFor(s, decision.getDecisionReason()), s.getSimulatedDecision(),
                reasonFor(s, null), s.getDecisionChanged(), s.getChangeType(), s.getCreatedAt()))
                .toList();
    }

    private DecisionRecord loadBaseDecision(String reference) {
        Transaction txn = transactionRepository.findByTransactionReference(reference)
                .orElseThrow(() -> new AnalyticsNotFoundException("Transaction not found: " + reference));
        return decisionRepository.findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId())
                .orElseThrow(() -> new AnalyticsNotFoundException("No decision recorded for transaction: " + reference));
    }

    private static String reasonFor(PolicySimulation s, String fallback) {
        return fallback != null ? fallback
                : ThresholdDecision.reason(s.getBaseRiskScore(), s.getReviewThreshold(),
                        s.getBlockThreshold(), s.getSimulatedDecision());
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AnalyticsValidationException(field + " must not be blank");
        }
        return value.trim();
    }

    private static double requiredThreshold(Double value, String field) {
        if (value == null) {
            throw new AnalyticsValidationException(field + " is required");
        }
        return value;
    }
}