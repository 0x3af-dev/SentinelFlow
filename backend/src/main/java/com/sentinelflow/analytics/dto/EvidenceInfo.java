package com.sentinelflow.analytics.dto;

import java.util.List;

/**
 * Compact evidence summary embedded in a decision replay: total node/edge
 * counts plus the bounded lineage subgraph.
 */
public record EvidenceInfo(
        int nodeCount,
        int edgeCount,
        List<EvidenceGraphDto.EvidenceNodeDto> nodes,
        List<EvidenceGraphDto.EvidenceEdgeDto> edges
) {
}