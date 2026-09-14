package com.sentinelflow.analytics.replay;

import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.DecisionInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.FeatureSnapshotInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.ModelInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.PolicyInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.RiskFactorInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.RiskScoreInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.RuleInfo;
import com.sentinelflow.analytics.dto.DecisionReplayResponse.TransactionInfo;
import com.sentinelflow.analytics.dto.DisagreementInfo;
import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.shared.EvidenceGraphCollector;
import com.sentinelflow.decision.DecisionPolicy;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.evidence.EvidenceEdge;
import com.sentinelflow.evidence.EvidenceEdgeRepository;
import com.sentinelflow.evidence.EvidenceNode;
import com.sentinelflow.evidence.EvidenceNodeRepository;
import com.sentinelflow.evidence.EvidenceNodeType;
import com.sentinelflow.evidence.EvidenceRelationshipType;
import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.FeatureSnapshotRepository;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.RiskFactor;
import com.sentinelflow.risk.RiskFactorRepository;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only reconstruction of the persisted decision lineage for a
 * transaction. Performs no ML inference, changes nothing, and requires the
 * involvement of no external system.
 */
@Service
public class DecisionReplayService {

    private final TransactionRepository transactionRepository;
    private final FeatureSnapshotRepository snapshotRepository;
    private final DecisionRecordRepository decisionRepository;
    private final RiskFactorRepository riskFactorRepository;
    private final EvidenceGraphCollector evidenceGraphCollector;
    private final EvidenceNodeRepository evidenceNodeRepository;
    private final EvidenceEdgeRepository evidenceEdgeRepository;

    public DecisionReplayService(TransactionRepository transactionRepository,
                                 FeatureSnapshotRepository snapshotRepository,
                                 DecisionRecordRepository decisionRepository,
                                 RiskFactorRepository riskFactorRepository,
                                 EvidenceGraphCollector evidenceGraphCollector,
                                 EvidenceNodeRepository evidenceNodeRepository,
                                 EvidenceEdgeRepository evidenceEdgeRepository) {
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.decisionRepository = decisionRepository;
        this.riskFactorRepository = riskFactorRepository;
        this.evidenceGraphCollector = evidenceGraphCollector;
        this.evidenceNodeRepository = evidenceNodeRepository;
        this.evidenceEdgeRepository = evidenceEdgeRepository;
    }

    @Transactional(readOnly = true)
    public DecisionReplayResponse replay(String transactionReference) {
        Transaction txn = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new AnalyticsNotFoundException(
                        "Transaction not found: " + transactionReference));

        DecisionRecord decision = decisionRepository
                .findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId())
                .orElseThrow(() -> new AnalyticsNotFoundException(
                        "No decision recorded for transaction: " + transactionReference));

        RiskScore riskScore = decision.getRiskScore();
        ModelVersion model = riskScore.getModelVersion();
        FeatureSnapshot snapshot = snapshotRepository
                .findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId())
                .orElse(null);
        List<RiskFactor> factors = riskFactorRepository.findByTransaction_Id(txn.getId());
        DecisionPolicy policy = decision.getPolicy();

        List<RuleInfo> triggeredRules = collectTriggeredRules(riskScore.getId());
        DisagreementInfo disagreement = DisagreementInfo.of(
                riskScore.getPrediction(), riskScore.getRiskScore(), triggeredRules);

        EvidenceGraphDto graph = evidenceGraphCollector.collect(
                txn.getId(),
                txn.getUser().getId(),
                txn.getDevice() != null ? txn.getDevice().getId() : null,
                txn.getLocation() != null ? txn.getLocation().getId() : null,
                txn.getMerchant().getId(),
                snapshot != null ? snapshot.getId() : null,
                model.getId(),
                riskScore.getId(),
                factors.stream().map(RiskFactor::getId).toList(),
                policy.getId(),
                decision.getId());

        Map<String, Object> config = policy.getConfiguration() == null ? Map.of() : policy.getConfiguration();
        Double review = getDouble(config, "review_threshold", 0.5);
        Double block = getDouble(config, "block_threshold", 0.85);

        return new DecisionReplayResponse(
                txn.getTransactionReference(),
                new TransactionInfo(
                        txn.getStatus().name(),
                        txn.getAmount(),
                        txn.getCurrency(),
                        txn.getChannel(),
                        txn.getTransactionType(),
                        txn.getTransactionTimestamp()),
                snapshot == null ? null : new FeatureSnapshotInfo(
                        snapshot.getId(),
                        snapshot.getFeatureSchemaVersion(),
                        snapshot.getGeneratedAt(),
                        snapshot.getFeatures()),
                new ModelInfo(model.getModelName(), model.getVersion(), model.getAlgorithm(),
                        model.getFeatureSchemaVersion()),
                new RiskScoreInfo(riskScore.getRiskScore(), riskScore.getPrediction(),
                        riskScore.getInferenceTimestamp(), riskScore.getInferenceLatencyMs()),
                factors.stream()
                        .map(f -> new RiskFactorInfo(f.getFactorType(), f.getDescription(),
                                f.getSeverity(), f.getSource()))
                        .toList(),
                triggeredRules,
                new PolicyInfo(policy.getPolicyName(), policy.getVersion(), review, block, config),
                new DecisionInfo(decision.getFinalDecision().name(), decision.getDecisionReason(),
                        decision.getDecisionTimestamp()),
                disagreement,
                new com.sentinelflow.analytics.dto.EvidenceInfo(graph.nodes().size(), graph.edges().size(),
                        graph.nodes(), graph.edges()));
    }

    /**
     * Triggered rules are not persisted relationally; they are recoverable from
     * RULE_RESULT evidence nodes that CONTRIBUTE_TO the risk score node.
     */
    private List<RuleInfo> collectTriggeredRules(UUID riskScoreId) {
        List<EvidenceNode> riskScoreNodes = evidenceNodeRepository
                .findByEntityTypeAndEntityId("RISK_SCORE", riskScoreId);
        if (riskScoreNodes.isEmpty()) {
            return List.of();
        }
        UUID riskScoreNodeId = riskScoreNodes.get(0).getId();
        return evidenceEdgeRepository.findByTargetNodeId(riskScoreNodeId).stream()
                .filter(e -> e.getRelationshipType() == EvidenceRelationshipType.CONTRIBUTES_TO)
                .map(EvidenceEdge::getSourceNode)
                .filter(n -> n.getNodeType() == EvidenceNodeType.RULE_RESULT)
                .map(n -> {
                    Map<String, Object> value = n.getValue() == null ? Map.of() : n.getValue();
                    return new RuleInfo(
                            str(value.get("rule_id")),
                            str(value.get("rule_version")),
                            str(value.get("severity")),
                            str(value.get("description")),
                            value.get("observed_values") instanceof Map<?, ?> m
                                    ? toObjectMap(m) : Map.of());
                })
                .toList();
    }

    private double getDouble(Map<String, Object> config, String key, double defaultValue) {
        Object v = config.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(v.toString());
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> toObjectMap(Map<?, ?> m) {
        return (Map) new java.util.HashMap<String, Object>((Map<? extends String, ?>) m);
    }
}