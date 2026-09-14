package com.sentinelflow.analytics.model;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicySimulationRepository extends JpaRepository<PolicySimulation, UUID> {
    List<PolicySimulation> findByTransactionReferenceOrderByCreatedAtDesc(String transactionReference);
}