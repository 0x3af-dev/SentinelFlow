package com.sentinelflow.analytics.counterfactual;

import com.sentinelflow.analytics.counterfactual.CounterfactualFeatureRegistry.FeatureSpec;
import com.sentinelflow.analytics.dto.CounterfactualRequest;
import com.sentinelflow.analytics.dto.CounterfactualRequest.CounterfactualFeatureModification;
import com.sentinelflow.analytics.dto.CounterfactualResponse;
import com.sentinelflow.analytics.dto.CounterfactualResponse.FeatureModificationInfo;
import com.sentinelflow.analytics.exception.AnalyticsMlUnavailableException;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.exception.AnalyticsValidationException;
import com.sentinelflow.analytics.model.CounterfactualAnalysis;
import com.sentinelflow.analytics.model.CounterfactualAnalysisRepository;
import com.sentinelflow.analytics.shared.ThresholdDecision;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.investigation.service.InvestigationEventPublisher;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
import com.sentinelflow.policy.PolicyEvaluator;
import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.FeatureSnapshotRepository;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.shared.dto.MlPrediction;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Counterfactual Engine. Re-scores a persisted feature snapshot with the
 * active model after applying bounded, validated numeric edits over an
 * immutable copy. The model call runs with no database transaction open (the
 * load step and the persist step are separate short transactions), matching the
 * production pipeline's rule that no DB transaction spans external ML calls.
 * The analysis is descriptive; it never mutates production lineage and never
 * claims causation.
 */
@Service
public class CounterfactualService {

    private static final Logger log = LoggerFactory.getLogger(CounterfactualService.class);

    private final TransactionRepository transactionRepository;
    private final FeatureSnapshotRepository snapshotRepository;
    private final DecisionRecordRepository decisionRepository;
    private final TransactionTemplate readOnlyTx;
    private final CounterfactualAnalysisRepository analysisRepository;
    private final CounterfactualFeatureRegistry featureRegistry;
    private final MlInferenceClient mlClient;
    private final PolicyEvaluator policyEvaluator;
    private final InvestigationEventPublisher investigationEvents;

    public CounterfactualService(TransactionRepository transactionRepository,
                                 FeatureSnapshotRepository snapshotRepository,
                                 DecisionRecordRepository decisionRepository,
                                 org.springframework.transaction.PlatformTransactionManager txManager,
                                 CounterfactualAnalysisRepository analysisRepository,
                                 CounterfactualFeatureRegistry featureRegistry,
                                 MlInferenceClient mlClient,
                                 PolicyEvaluator policyEvaluator,
                                 InvestigationEventPublisher investigationEvents) {
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.decisionRepository = decisionRepository;
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
        this.analysisRepository = analysisRepository;
        this.featureRegistry = featureRegistry;
        this.mlClient = mlClient;
        this.policyEvaluator = policyEvaluator;
        this.investigationEvents = investigationEvents;
    }

    public CounterfactualResponse analyze(CounterfactualRequest request) {
        String reference = requiredText(request.transactionReference(), "transactionReference");

        // Load base lineage values inside their own short read-only transaction;
        // the returned record contains only detached, self-contained data.
        CounterfactualBase base = loadBase(reference);
        List<FeatureModificationInfo> applied = resolveModifications(request, base);

        // Model inference runs OUTSIDE any database transaction.
        Map<String, Object> modifiedFeatures = new LinkedHashMap<>(base.features());
        for (FeatureModificationInfo m : applied) {
            modifiedFeatures.put(m.feature(), m.modifiedValue());
        }

        MlPrediction hypothesis;
        try {
            hypothesis = mlClient.infer(MlInferenceRequest.fromFeatures(modifiedFeatures));
        } catch (MlInferenceClient.MlInferenceException e) {
            log.warn("Counterfactual inference failed for {}: {}", reference, e.getMessage());
            throw new AnalyticsMlUnavailableException(
                    "Model inference unavailable for counterfactual analysis: " + e.getMessage(), e);
        }

        if (hypothesis.riskScore() == null || hypothesis.riskScore() < 0.0 || hypothesis.riskScore() > 1.0) {
            throw new AnalyticsValidationException(
                    "Hypothetical risk score out of range [0,1]: " + hypothesis.riskScore());
        }

        // Hypothetical decision uses the currently active production policy.
        double review = 0.5;
        double block = 0.85;
        try {
            Map<String, Object> config = policyEvaluator.getActivePolicy().getConfiguration();
            review = getDouble(config, "review_threshold", 0.5);
            block = getDouble(config, "block_threshold", 0.85);
        } catch (RuntimeException e) {
            log.warn("Active policy unavailable for counterfactual {}; using defaults: {}", reference, e.getMessage());
        }

        double originalScore = base.originalRiskScore();
        double hypotheticalScore = hypothesis.riskScore();
        String originalDecision = base.originalDecision();
        String hypotheticalDecision = ThresholdDecision.evaluate(hypotheticalScore, review, block);
        boolean changed = !originalDecision.equals(hypotheticalDecision);

        CounterfactualAnalysis saved = analysisRepository.save(new CounterfactualAnalysis(
                reference, base.snapshotId(), base.featureSchemaVersion(),
                hypothesis.modelName(), hypothesis.modelVersion(),
                applied.stream().map(m -> Map.<String, Object>of(
                        "feature", m.feature(),
                        "originalValue", m.originalValue(),
                        "modifiedValue", m.modifiedValue()))
                        .toList(),
                originalScore, hypotheticalScore, hypotheticalScore - originalScore,
                originalDecision, hypotheticalDecision, changed, request.requestedBy()));

        if (request.investigationId() != null) {
            investigationEvents.record(request.investigationId(), reference,
                    "COUNTERFACTUAL_EXECUTED", "ANALYST", request.requestedBy(), Map.of(
                            "analysisId", saved.getId().toString(),
                            "originalScore", originalScore,
                            "hypotheticalScore", hypotheticalScore,
                            "hypotheticalDecision", hypotheticalDecision,
                            "decisionChanged", changed));
        }

        return new CounterfactualResponse(
                saved.getId(), reference, base.snapshotId(), base.featureSchemaVersion(),
                hypothesis.modelName(), hypothesis.modelVersion(), applied,
                originalScore, hypotheticalScore, hypotheticalScore - originalScore,
                originalDecision, hypotheticalDecision, changed,
                ThresholdDecision.changeType(originalDecision, hypotheticalDecision),
                saved.getCreatedAt(), CounterfactualResponse.DISCLAIMER);
    }

    public List<CounterfactualResponse> listCounterfactuals(String transactionReference) {
        String reference = requiredText(transactionReference, "transactionReference");
        return analysisRepository.findByTransactionReferenceOrderByCreatedAtDesc(reference).stream()
                .map(a -> new CounterfactualResponse(
                        a.getId(), a.getTransactionReference(), a.getFeatureSnapshotId(),
                        a.getFeatureSchemaVersion(), a.getModelName(), a.getModelVersion(),
                        a.getModifications().stream()
                                .map(m -> new FeatureModificationInfo(
                                        String.valueOf(m.get("feature")),
                                        m.get("originalValue"), m.get("modifiedValue")))
                                .toList(),
                        a.getOriginalRiskScore(), a.getHypotheticalRiskScore(), a.getScoreDelta(),
                        a.getOriginalDecision(), a.getHypotheticalDecision(), a.getDecisionChanged(),
                        ThresholdDecision.changeType(a.getOriginalDecision(), a.getHypotheticalDecision()),
                        a.getCreatedAt(), CounterfactualResponse.DISCLAIMER))
                .toList();
    }

    /**
     * Validates every requested modification WITHOUT calling the model. Any
     * unsupported feature, non-numeric or non-finite value, out-of-domain
     * value, fractional integral value, or duplicate feature aborts the whole
     * analysis.
     */
    private List<FeatureModificationInfo> resolveModifications(CounterfactualRequest request, CounterfactualBase base) {
        if (request.modifications() == null || request.modifications().isEmpty()) {
            throw new AnalyticsValidationException("modifications must not be empty");
        }

        Set<String> seen = new HashSet<>();
        List<FeatureModificationInfo> resolved = new ArrayList<>();
        for (CounterfactualFeatureModification mod : request.modifications()) {
            String feature = requiredText(mod.feature(), "modifications.feature");
            FeatureSpec spec = featureRegistry.spec(feature);
            if (spec == null) {
                throw new AnalyticsValidationException(
                        "Unsupported feature for counterfactual modification: " + feature);
            }
            if (!seen.add(feature)) {
                throw new AnalyticsValidationException("Duplicate feature modification: " + feature);
            }

            double value = toFiniteDouble(mod.value(), feature);
            if (spec.integral() && Math.rint(value) != value) {
                throw new AnalyticsValidationException(
                        "Feature " + feature + " must be a whole number, got " + value);
            }
            if (!spec.withinBounds(value)) {
                throw new AnalyticsValidationException(String.format(
                        "Value %.4f for feature %s is outside allowed domain [%.2f, %.2f]",
                        value, feature, spec.min(), spec.max()));
            }

            Object original = base.features().get(feature);
            resolved.add(new FeatureModificationInfo(feature, original, value));
        }
        return resolved;
    }

    private static double toFiniteDouble(Object value, String feature) {
        if (value == null) {
            throw new AnalyticsValidationException("Value for feature " + feature + " must not be null");
        }
        double d;
        if (value instanceof Number n) {
            d = n.doubleValue();
        } else {
            throw new AnalyticsValidationException(
                    "Value for feature " + feature + " must be numeric, got " + value.getClass().getSimpleName());
        }
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new AnalyticsValidationException("Value for feature " + feature + " must be finite");
        }
        return d;
    }

    private CounterfactualBase loadBase(String reference) {
        return readOnlyTx.execute(status -> {
            Transaction txn = transactionRepository.findByTransactionReference(reference)
                    .orElseThrow(() -> new AnalyticsNotFoundException("Transaction not found: " + reference));
            DecisionRecord decision = decisionRepository
                    .findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId())
                    .orElseThrow(() -> new AnalyticsNotFoundException(
                            "No decision recorded for transaction: " + reference));
            RiskScore riskScore = decision.getRiskScore();
            FeatureSnapshot snapshot = snapshotRepository
                    .findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId())
                    .orElseThrow(() -> new AnalyticsNotFoundException(
                            "No feature snapshot recorded for transaction: " + reference));
            ModelVersion model = riskScore.getModelVersion();

            return new CounterfactualBase(
                    reference,
                    new LinkedHashMap<>(snapshot.getFeatures()),
                    snapshot.getId(),
                    snapshot.getFeatureSchemaVersion(),
                    model.getModelName(),
                    model.getVersion(),
                    riskScore.getRiskScore(),
                    decision.getFinalDecision().name());
        });
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AnalyticsValidationException(field + " must not be blank");
        }
        return value.trim();
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object v = map == null ? null : map.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(v.toString());
    }

    private record CounterfactualBase(
            String transactionReference,
            Map<String, Object> features,
            UUID snapshotId,
            String featureSchemaVersion,
            String modelName,
            String modelVersion,
            double originalRiskScore,
            String originalDecision
    ) {
    }
}