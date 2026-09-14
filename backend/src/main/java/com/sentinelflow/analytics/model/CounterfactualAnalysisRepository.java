package com.sentinelflow.analytics.model;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CounterfactualAnalysisRepository extends JpaRepository<CounterfactualAnalysis, UUID> {
    List<CounterfactualAnalysis> findByTransactionReferenceOrderByCreatedAtDesc(String transactionReference);
}