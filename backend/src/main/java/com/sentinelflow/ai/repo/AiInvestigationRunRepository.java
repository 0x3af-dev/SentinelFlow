package com.sentinelflow.ai.repo;

import com.sentinelflow.ai.model.AiInvestigationRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiInvestigationRunRepository extends JpaRepository<AiInvestigationRun, UUID> {

    List<AiInvestigationRun> findByInvestigationIdOrderByCreatedAtDesc(UUID investigationId);
}