package com.sentinelflow.pipeline;

import com.sentinelflow.decision.DecisionPolicy;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.FinalDecision;
import com.sentinelflow.decision.service.DecisionService;
import com.sentinelflow.enrichment.TransactionEnricher;
import com.sentinelflow.evidence.service.EvidenceService;
import com.sentinelflow.feature.FeatureComputationService;
import com.sentinelflow.metrics.SentinelFlowMetrics;
import com.sentinelflow.observability.Md;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
import com.sentinelflow.policy.PolicyEvaluator;
import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.RiskFactor;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.risk.service.FeatureSnapshotService;
import com.sentinelflow.risk.service.RiskScoringService;
import com.sentinelflow.rule.RuleEngine;
import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import com.sentinelflow.shared.dto.MlPrediction;
import com.sentinelflow.shared.dto.PipelineResult;
import com.sentinelflow.shared.dto.PolicyEvaluationResult;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import com.sentinelflow.transaction.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionIntelligencePipeline {

    private static final Logger log = LoggerFactory.getLogger(TransactionIntelligencePipeline.class);

    private final TransactionRepository transactionRepository;
    private final TransactionEnricher enricher;
    private final FeatureComputationService featureComputation;
    private final FeatureSnapshotService featureSnapshotService;
    private final MlInferenceClient mlClient;
    private final RiskScoringService riskScoringService;
    private final RuleEngine ruleEngine;
    private final PolicyEvaluator policyEvaluator;
    private final DecisionService decisionService;
    private final EvidenceService evidenceService;
    private final SentinelFlowMetrics metrics;

    public TransactionIntelligencePipeline(TransactionRepository transactionRepository,
                                           TransactionEnricher enricher,
                                           FeatureComputationService featureComputation,
                                           FeatureSnapshotService featureSnapshotService,
                                           MlInferenceClient mlClient,
                                           RiskScoringService riskScoringService,
                                           RuleEngine ruleEngine,
                                           PolicyEvaluator policyEvaluator,
                                           DecisionService decisionService,
                                           EvidenceService evidenceService,
                                           SentinelFlowMetrics metrics) {
        this.transactionRepository = transactionRepository;
        this.enricher = enricher;
        this.featureComputation = featureComputation;
        this.featureSnapshotService = featureSnapshotService;
        this.mlClient = mlClient;
        this.riskScoringService = riskScoringService;
        this.ruleEngine = ruleEngine;
        this.policyEvaluator = policyEvaluator;
        this.decisionService = decisionService;
        this.evidenceService = evidenceService;
        this.metrics = metrics;
    }

    public PipelineResult process(String transactionReference) {
        metrics.transactionProcessingStarted();
        var sample = metrics.transactionProcessingSample();
        return Md.run(Md.of(Md.OP_PIPELINE, null, transactionReference), () -> {
            try {
                PipelineResult result = processInternal(transactionReference);
                metrics.transactionProcessingSucceeded(result.decision());
                return result;
            } catch (PipelineException e) {
                metrics.transactionProcessingFailed(stageOf(e.getMessage()));
                throw e;
            } catch (Exception e) {
                metrics.transactionProcessingFailed(infrastructureStage(e));
                throw e;
            } finally {
                metrics.stopTransactionProcessing(sample);
            }
        });
    }

    private PipelineResult processInternal(String transactionReference) {
        Instant pipelineStart = Instant.now();
        log.info("Starting pipeline for transaction: {}", transactionReference);

        // 1. Load transaction
        Transaction transaction = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new PipelineException("Transaction not found: " + transactionReference));

        updateTransactionStatus(transaction, TransactionStatus.ENRICHING);

        // 2. Enrichment
        EnrichmentContext enrichment;
        try {
            enrichment = enricher.enrich(transaction);
        } catch (Exception e) {
            updateTransactionStatus(transaction, TransactionStatus.FAILED);
            throw new PipelineException("Enrichment failed: " + e.getMessage(), e);
        }

        // 3. Feature Computation
        FeatureSet featureSet;
        try {
            featureSet = featureComputation.compute(enrichment);
        } catch (Exception e) {
            updateTransactionStatus(transaction, TransactionStatus.FAILED);
            throw new PipelineException("Feature computation failed: " + e.getMessage(), e);
        }

        // 4. Feature Snapshot Persistence
        FeatureSnapshot featureSnapshot;
        try {
            featureSnapshot = featureSnapshotService.create(transaction, featureSet);
        } catch (Exception e) {
            updateTransactionStatus(transaction, TransactionStatus.FAILED);
            throw new PipelineException("Feature snapshot persistence failed: " + e.getMessage(), e);
        }

        updateTransactionStatus(transaction, TransactionStatus.SCORING);

        // 5. ML Inference
        MlPrediction mlPrediction;
        try {
            MlInferenceRequest mlRequest = MlInferenceRequest.fromFeatures(featureSet.features());
            mlPrediction = mlClient.infer(mlRequest);
        } catch (MlInferenceClient.MlInferenceException e) {
            updateTransactionStatus(transaction, TransactionStatus.FAILED);
            throw new PipelineException("ML inference failed: " + e.getMessage(), e);
        }

        // 6. Risk Score + Risk Factor Persistence
        RiskScore riskScore;
        try {
            riskScore = riskScoringService.createRiskScore(transaction, mlPrediction, featureSnapshot);
        } catch (Exception e) {
            updateTransactionStatus(transaction, TransactionStatus.FAILED);
            throw new PipelineException("Risk score persistence failed: " + e.getMessage(), e);
        }

        // 7. Rule Evaluation
        RuleEngine.RuleEvaluationResult ruleResult;
        try {
            ruleResult = ruleEngine.evaluate(enrichment, featureSet);
        } catch (Exception e) {
            updateTransactionStatus(transaction, TransactionStatus.FAILED);
            throw new PipelineException("Rule evaluation failed: " + e.getMessage(), e);
        }

        // 8. Policy Evaluation
        PolicyEvaluationResult policyResult;
        try {
            policyResult = policyEvaluator.evaluate(riskScore, ruleResult);
        } catch (Exception e) {
            updateTransactionStatus(transaction, TransactionStatus.FAILED);
            throw new PipelineException("Policy evaluation failed: " + e.getMessage(), e);
        }

        // 9. Decision Persistence
        DecisionPolicy policy = getActivePolicy();
        DecisionRecord decision;
        try {
            decision = decisionService.createDecision(transaction, riskScore, policy, policyResult);
        } catch (Exception e) {
            updateTransactionStatus(transaction, TransactionStatus.FAILED);
            throw new PipelineException("Decision persistence failed: " + e.getMessage(), e);
        }

        // 10. Evidence Generation
        List<RiskFactor> riskFactors = riskScoringService.findRiskFactorsByTransaction(transaction.getId());
        try {
            evidenceService.buildEvidenceGraph(
                    transaction, enrichment, featureSet, featureSnapshot,
                    mlPrediction, riskScore, riskFactors, ruleResult,
                    policyResult, decision, policy
            );
        } catch (Exception e) {
            // Evidence failure should not fail the entire pipeline
            log.warn("Evidence generation failed for transaction {}: {}", transactionReference, e.getMessage());
        }

        updateTransactionStatus(transaction, TransactionStatus.COMPLETED);

        Instant pipelineEnd = Instant.now();
        log.info("Pipeline completed for transaction: {} in {}ms", transactionReference,
                java.time.Duration.between(pipelineStart, pipelineEnd).toMillis());

        List<Map<String, Object>> riskFactorsOut = mlPrediction.riskFactors().stream()
                .map(rf -> Map.of(
                        "factor_type", rf.factorType(),
                        "description", rf.description(),
                        "severity", rf.severity(),
                        "details", rf.details()
                ))
                .collect(Collectors.toList());

        return new PipelineResult(
                transactionReference,
                "COMPLETED",
                featureSnapshot.getId().toString(),
                mlPrediction.modelVersion(),
                mlPrediction.riskScore(),
                riskFactorsOut,
                policyResult.decision(),
                policyResult.reason(),
                policyResult.policyVersion(),
                decision.getDecisionTimestamp(),
                transaction.getId().toString(),
                pipelineStart,
                pipelineEnd
        );
    }

    private DecisionPolicy getActivePolicy() {
        return policyEvaluator.getActivePolicy();
    }

    private void updateTransactionStatus(Transaction transaction, TransactionStatus status) {
        transaction.setStatus(status);
        transactionRepository.saveAndFlush(transaction);
    }

    /**
     * Maps a PipelineException to the pipeline stage that failed for metric
     * tagging. Only this class produces these messages, so the prefix match is
     * deterministic and single-sourced.
     */
    private static String stageOf(String message) {
        if (message == null) return "PIPELINE";
        if (message.startsWith("Transaction not found")) return "TRANSACTION_LOOKUP";
        if (message.contains("Enrichment failed")) return "ENRICHMENT";
        if (message.contains("Feature computation failed")) return "FEATURE_COMPUTATION";
        if (message.contains("Feature snapshot persistence failed")) return "FEATURE_SNAPSHOT";
        if (message.contains("ML inference failed")) return "ML_INFERENCE";
        if (message.contains("Risk score persistence failed")) return "RISK_SCORE";
        if (message.contains("Rule evaluation failed")) return "RULE_EVALUATION";
        if (message.contains("Policy evaluation failed")) return "POLICY_EVALUATION";
        if (message.contains("Decision persistence failed")) return "DECISION_PERSISTENCE";
        return "PIPELINE";
    }

    /** Failures that escape a stage wrapper (e.g. database unavailable). */
    private static String infrastructureStage(Exception e) {
        if (e instanceof DataAccessException) return "DATABASE";
        return "INFRASTRUCTURE";
    }

    public static class PipelineException extends RuntimeException {
        public PipelineException(String message) {
            super(message);
        }

        public PipelineException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}