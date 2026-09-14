package com.sentinelflow.analytics.shared;

import com.sentinelflow.analytics.dto.EvidenceGraphDto;
import com.sentinelflow.analytics.dto.EvidenceGraphDto.EvidenceEdgeDto;
import com.sentinelflow.analytics.dto.EvidenceGraphDto.EvidenceNodeDto;
import com.sentinelflow.evidence.EvidenceEdge;
import com.sentinelflow.evidence.EvidenceEdgeRepository;
import com.sentinelflow.evidence.EvidenceNode;
import com.sentinelflow.evidence.EvidenceNodeRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Collects the bounded evidence subgraph for a single transaction's decision
 * lineage. Nodes are resolved by the known lineage entity ids (transaction,
 * user, device, location, merchant, feature snapshot, model version, risk
 * score, risk factors, policy, decision). RULE_RESULT nodes are discovered
 * through CONTRIBUTES_TO edges directed at the risk score node. No arbitrary
 * recursion is used; the node set is bounded to the lineage of one transaction.
 */
@Component
public class EvidenceGraphCollector {

    private final EvidenceNodeRepository nodeRepository;
    private final EvidenceEdgeRepository edgeRepository;

    public EvidenceGraphCollector(EvidenceNodeRepository nodeRepository, EvidenceEdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    public EvidenceGraphDto collect(UUID transactionId, UUID userId, UUID deviceId, UUID locationId,
                                    UUID merchantId, UUID snapshotId, UUID modelVersionId,
                                    UUID riskScoreId, List<UUID> riskFactorIds, UUID policyId,
                                    UUID decisionId) {
        Map<UUID, EvidenceNode> nodes = new LinkedHashMap<>();

        addNode(nodes, "TRANSACTION", transactionId);
        addNode(nodes, "USER", userId);
        addNode(nodes, "DEVICE", deviceId);
        addNode(nodes, "LOCATION", locationId);
        addNode(nodes, "MERCHANT", merchantId);
        addNode(nodes, "FEATURE_SNAPSHOT", snapshotId);
        addNode(nodes, "MODEL_VERSION", modelVersionId);
        addNode(nodes, "RISK_SCORE", riskScoreId);
        for (UUID factorId : riskFactorIds) {
            addNode(nodes, "RISK_FACTOR", factorId);
        }
        addNode(nodes, "DECISION_POLICY", policyId);
        addNode(nodes, "DECISION_RECORD", decisionId);

        // RULE_RESULT nodes point into the risk score node via CONTRIBUTES_TO
        addRuleResultNodes(nodes, riskScoreId);

        List<EvidenceEdge> edges = new ArrayList<>();
        for (UUID nodeId : nodes.keySet()) {
            for (EvidenceEdge edge : edgeRepository.findBySourceNodeId(nodeId)) {
                if (nodes.containsKey(edge.getTargetNode().getId())) {
                    edges.add(edge);
                }
            }
            for (EvidenceEdge edge : edgeRepository.findByTargetNodeId(nodeId)) {
                if (nodes.containsKey(edge.getSourceNode().getId()) && !edges.contains(edge)) {
                    edges.add(edge);
                }
            }
        }

        List<EvidenceNodeDto> nodeDtos = nodes.values().stream()
                .map(n -> new EvidenceNodeDto(n.getId(), n.getNodeType().name(), n.getSourceType().name(),
                        n.getEntityType(), n.getEntityId(), n.getObservedAt(), n.getValue()))
                .toList();
        List<EvidenceEdgeDto> edgeDtos = edges.stream()
                .map(e -> new EvidenceEdgeDto(e.getId(), e.getSourceNode().getId(), e.getTargetNode().getId(),
                        e.getRelationshipType().name()))
                .toList();

        return new EvidenceGraphDto(nodeDtos, edgeDtos);
    }

    private void addNode(Map<UUID, EvidenceNode> nodes, String entityType, UUID entityId) {
        if (entityId == null) {
            return;
        }
        for (EvidenceNode existing : nodeRepository.findByEntityTypeAndEntityId(entityType, entityId)) {
            nodes.put(existing.getId(), existing);
        }
    }

    private void addRuleResultNodes(Map<UUID, EvidenceNode> nodes, UUID riskScoreId) {
        List<EvidenceNode> riskScoreNodes = nodeRepository.findByEntityTypeAndEntityId("RISK_SCORE", riskScoreId);
        if (riskScoreNodes.isEmpty()) {
            return;
        }
        UUID riskScoreNodeId = riskScoreNodes.get(0).getId();
        for (EvidenceEdge edge : edgeRepository.findByTargetNodeId(riskScoreNodeId)) {
            if (edge.getRelationshipType() == com.sentinelflow.evidence.EvidenceRelationshipType.CONTRIBUTES_TO
                    && edge.getSourceNode().getNodeType() == com.sentinelflow.evidence.EvidenceNodeType.RULE_RESULT) {
                EvidenceNode ruleNode = edge.getSourceNode();
                nodes.put(ruleNode.getId(), ruleNode);
            }
        }
    }
}