package com.sentinelflow.investigation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationEventRepository extends JpaRepository<InvestigationEvent, UUID> {
    List<InvestigationEvent> findByInvestigationId(UUID investigationId);
}