package com.sentinelflow.evidence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceEdgeRepository extends JpaRepository<EvidenceEdge, UUID> {
    List<EvidenceEdge> findBySourceNodeId(UUID sourceNodeId);
    List<EvidenceEdge> findByTargetNodeId(UUID targetNodeId);

    @Query("SELECT e FROM EvidenceEdge e WHERE e.sourceNode.id = :sourceId AND e.targetNode.id = :targetId AND e.relationshipType = :relType")
    List<EvidenceEdge> findBySourceNodeIdAndTargetNodeIdAndRelationshipType(UUID sourceId, UUID targetId, EvidenceRelationshipType relType);
}