package com.sentinelflow.evidence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceNodeRepository extends JpaRepository<EvidenceNode, UUID> {
    List<EvidenceNode> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    List<EvidenceNode> findByNodeType(EvidenceNodeType nodeType);
}