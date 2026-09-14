package com.sentinelflow.analytics.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A bounded evidence subgraph for a single transaction's decision lineage.
 * Nodes are collected from persisted evidence rows by referencing the known
 * lineage entities; no arbitrary, recursive graph traversal is performed.
 */
public record EvidenceGraphDto(List<EvidenceNodeDto> nodes, List<EvidenceEdgeDto> edges) {

    public record EvidenceNodeDto(
            UUID id,
            String nodeType,
            String sourceType,
            String entityType,
            UUID entityId,
            java.time.Instant observedAt,
            Map<String, Object> value
    ) {
    }

    public record EvidenceEdgeDto(
            UUID id,
            UUID sourceNodeId,
            UUID targetNodeId,
            String relationshipType
    ) {
    }
}