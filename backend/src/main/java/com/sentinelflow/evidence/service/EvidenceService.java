package com.sentinelflow.evidence.service;

import com.sentinelflow.decision.DecisionPolicy;
import com.sentinelflow.decision.DecisionRecord;
import com.sentinelflow.evidence.EvidenceEdge;
import com.sentinelflow.evidence.EvidenceEdgeRepository;
import com.sentinelflow.evidence.EvidenceNode;
import com.sentinelflow.evidence.EvidenceNodeRepository;
import com.sentinelflow.evidence.EvidenceNodeType;
import com.sentinelflow.evidence.EvidenceRelationshipType;
import com.sentinelflow.evidence.EvidenceSourceType;
import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.ModelVersion;
import com.sentinelflow.risk.RiskFactor;
import com.sentinelflow.risk.RiskScore;
import com.sentinelflow.rule.RuleEngine;
import com.sentinelflow.rule.RuleResult;
import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import com.sentinelflow.shared.dto.MlPrediction;
import com.sentinelflow.shared.dto.PolicyEvaluationResult;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final EvidenceNodeRepository nodeRepository;
    private final EvidenceEdgeRepository edgeRepository;

    public EvidenceService(EvidenceNodeRepository nodeRepository, EvidenceEdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @Transactional
    public EvidenceNode buildEvidenceGraph(Transaction transaction,
                                           EnrichmentContext enrichment,
                                           FeatureSet featureSet,
                                           FeatureSnapshot featureSnapshot,
                                           MlPrediction mlPrediction,
                                           RiskScore riskScore,
                                           List<RiskFactor> riskFactors,
                                           RuleEngine.RuleEvaluationResult ruleResult,
                                           PolicyEvaluationResult policyResult,
                                           DecisionRecord decision,
                                           DecisionPolicy policy) {

        log.debug("Building evidence graph for transaction: {}", transaction.getTransactionReference());

        // 1. Create core nodes
        EvidenceNode txnNode = createOrGetNode(EvidenceNodeType.TRANSACTION, EvidenceSourceType.TRANSACTION_RECORD,
                "TRANSACTION", transaction.getId(), Map.of(
                "transaction_reference", transaction.getTransactionReference(),
                "amount", transaction.getAmount(),
                "currency", transaction.getCurrency(),
                "timestamp", transaction.getTransactionTimestamp()
        ));

        EvidenceNode userNode = createOrGetNode(EvidenceNodeType.USER, EvidenceSourceType.USER_ACTIVITY,
                "USER", enrichment.user().getId(), Map.of(
                "external_reference", enrichment.user().getExternalReference(),
                "display_name", enrichment.user().getDisplayName()
        ));

        // 2. Device node
        EvidenceNode deviceNode = null;
        if (enrichment.device() != null) {
            deviceNode = createOrGetNode(EvidenceNodeType.DEVICE, EvidenceSourceType.DEVICE_HISTORY,
                    "DEVICE", enrichment.device().getId(), Map.of(
                    "device_reference", enrichment.device().getDeviceReference(),
                    "device_type", enrichment.device().getDeviceType(),
                    "platform", enrichment.device().getPlatform(),
                    "is_new_device", enrichment.deviceProfile().isNewDevice()
            ));
            createEdgeIfNotExists(deviceNode, txnNode, EvidenceRelationshipType.USED_DEVICE);
        }

        // 3. Location node
        EvidenceNode locationNode = null;
        if (enrichment.location() != null) {
            locationNode = createOrGetNode(EvidenceNodeType.LOCATION, EvidenceSourceType.LOCATION_HISTORY,
                    "LOCATION", enrichment.location().getId(), Map.of(
                    "country", enrichment.location().getCountry(),
                    "region", enrichment.location().getRegion(),
                    "city", enrichment.location().getCity(),
                    "is_new_location", enrichment.locationProfile().isNewLocation()
            ));
            createEdgeIfNotExists(locationNode, txnNode, EvidenceRelationshipType.OCCURRED_AT);
        }

        // 4. Merchant node
        EvidenceNode merchantNode = createOrGetNode(EvidenceNodeType.MERCHANT, EvidenceSourceType.TRANSACTION_RECORD,
                "MERCHANT", enrichment.merchant().getId(), Map.of(
                "external_reference", enrichment.merchant().getExternalReference(),
                "name", enrichment.merchant().getName(),
                "category", enrichment.merchant().getCategory(),
                "country", enrichment.merchant().getCountry()
        ));
        createEdgeIfNotExists(merchantNode, txnNode, EvidenceRelationshipType.ASSOCIATED_WITH);

        // 5. Transaction -> User edge
        createEdgeIfNotExists(txnNode, userNode, EvidenceRelationshipType.PERFORMED_BY);

        // 6. Feature node
        EvidenceNode featureNode = createOrGetNode(EvidenceNodeType.FEATURE, EvidenceSourceType.MODEL_OUTPUT,
                "FEATURE_SNAPSHOT", featureSnapshot.getId(), Map.of(
                "feature_schema_version", featureSet.featureSchemaVersion(),
                "feature_count", featureSet.features().size(),
                "generated_at", featureSnapshot.getGeneratedAt()
        ));
        createEdgeIfNotExists(txnNode, featureNode, EvidenceRelationshipType.GENERATED_FEATURE);

        // 7. Model node
        ModelVersion modelVersion = riskScore.getModelVersion();
        EvidenceNode modelNode = createOrGetNode(EvidenceNodeType.MODEL_PREDICTION, EvidenceSourceType.MODEL_OUTPUT,
                "MODEL_VERSION", modelVersion.getId(), Map.of(
                "model_name", modelVersion.getModelName(),
                "version", modelVersion.getVersion(),
                "algorithm", modelVersion.getAlgorithm(),
                "feature_schema_version", modelVersion.getFeatureSchemaVersion()
        ));
        createEdgeIfNotExists(featureNode, modelNode, EvidenceRelationshipType.USED_BY_MODEL);

        // 8. Risk Score node
        EvidenceNode riskScoreNode = createOrGetNode(EvidenceNodeType.MODEL_PREDICTION, EvidenceSourceType.MODEL_OUTPUT,
                "RISK_SCORE", riskScore.getId(), Map.of(
                "risk_score", riskScore.getRiskScore(),
                "prediction", riskScore.getPrediction(),
                "inference_latency_ms", riskScore.getInferenceLatencyMs(),
                "inference_timestamp", riskScore.getInferenceTimestamp()
        ));
        createEdgeIfNotExists(modelNode, riskScoreNode, EvidenceRelationshipType.PRODUCED);

        // 9. Risk Factor nodes
        for (RiskFactor factor : riskFactors) {
            EvidenceNode factorNode = createOrGetNode(EvidenceNodeType.RISK_FACTOR, EvidenceSourceType.MODEL_OUTPUT,
                    "RISK_FACTOR", factor.getId(), Map.of(
                    "factor_type", factor.getFactorType(),
                    "description", factor.getDescription(),
                    "severity", factor.getSeverity(),
                    "source", factor.getSource()
            ));
            createEdgeIfNotExists(factorNode, riskScoreNode, EvidenceRelationshipType.CONTRIBUTES_TO);
        }

        // 10. Rule Result nodes
        for (RuleResult ruleResultItem : ruleResult.triggeredRules()) {
            EvidenceNode ruleNode = createOrGetNode(EvidenceNodeType.RULE_RESULT, EvidenceSourceType.RULE_ENGINE,
                    "RULE_RESULT", UUID.randomUUID(), Map.of(
                    "rule_id", ruleResultItem.ruleId(),
                    "rule_version", ruleResultItem.ruleVersion(),
                    "severity", ruleResultItem.severity(),
                    "description", ruleResultItem.description(),
                    "observed_values", ruleResultItem.observedValues()
            ));
            createEdgeIfNotExists(ruleNode, riskScoreNode, EvidenceRelationshipType.CONTRIBUTES_TO);
        }

        // 11. Policy node
        EvidenceNode policyNode = createOrGetNode(EvidenceNodeType.POLICY, EvidenceSourceType.POLICY_ENGINE,
                "DECISION_POLICY", policy.getId(), Map.of(
                "policy_name", policy.getPolicyName(),
                "version", policy.getVersion(),
                "configuration", policy.getConfiguration()
        ));
        createEdgeIfNotExists(policyNode, riskScoreNode, EvidenceRelationshipType.GOVERNED_BY);

        // 12. Decision node
        EvidenceNode decisionNode = createOrGetNode(EvidenceNodeType.DECISION, EvidenceSourceType.POLICY_ENGINE,
                "DECISION_RECORD", decision.getId(), Map.of(
                "final_decision", decision.getFinalDecision().name(),
                "decision_timestamp", decision.getDecisionTimestamp(),
                "decision_reason", decision.getDecisionReason(),
                "risk_score_id", riskScore.getId(),
                "policy_id", policy.getId()
        ));
        createEdgeIfNotExists(riskScoreNode, decisionNode, EvidenceRelationshipType.PRODUCED);
        createEdgeIfNotExists(policyNode, decisionNode, EvidenceRelationshipType.GOVERNED_BY);

        // 13. Transaction -> Decision edge
        createEdgeIfNotExists(txnNode, decisionNode, EvidenceRelationshipType.PRODUCED);

        log.info("Evidence graph built for transaction: {} with {} nodes",
                transaction.getTransactionReference(), countNodes(transaction));

        return txnNode;
    }

    private EvidenceNode createOrGetNode(EvidenceNodeType nodeType, EvidenceSourceType sourceType,
                                         String entityType, UUID entityId, Map<String, Object> value) {
        // Check if node already exists
        List<EvidenceNode> existing = nodeRepository.findByEntityTypeAndEntityId(entityType, entityId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        EvidenceNode node = new EvidenceNode(
                nodeType,
                sourceType,
                entityType,
                entityId,
                Instant.now(),
                value,
                1.0,
                Map.of()
        );
        return nodeRepository.save(node);
    }

    private void createEdgeIfNotExists(EvidenceNode source, EvidenceNode target, EvidenceRelationshipType relationship) {
        // Check if edge already exists
        List<EvidenceEdge> existing = edgeRepository.findBySourceNodeIdAndTargetNodeIdAndRelationshipType(
                source.getId(), target.getId(), relationship);
        if (existing.isEmpty()) {
            EvidenceEdge edge = new EvidenceEdge(
                    source,
                    target,
                    relationship,
                    Map.of()
            );
            edgeRepository.save(edge);
        }
    }

    private long countNodes(Transaction transaction) {
        return nodeRepository.findByEntityTypeAndEntityId("TRANSACTION", transaction.getId()).size()
                + nodeRepository.findByEntityTypeAndEntityId("USER", transaction.getUser().getId()).size();
    }
}