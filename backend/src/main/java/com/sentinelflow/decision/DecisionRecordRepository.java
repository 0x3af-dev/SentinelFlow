package com.sentinelflow.decision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, UUID> {
    List<DecisionRecord> findByTransactionId(UUID transactionId);

    List<DecisionRecord> findByTransaction_Id(UUID transactionId);

    @Query("SELECT d FROM DecisionRecord d WHERE d.transaction.id = :transactionId ORDER BY d.createdAt DESC")
    Optional<DecisionRecord> findFirstByTransactionIdOrderByCreatedAtDesc(UUID transactionId);

    @Query("SELECT d FROM DecisionRecord d WHERE d.transaction.id = :transactionId AND d.riskScore.id = :riskScoreId AND d.policy.id = :policyId")
    Optional<DecisionRecord> findByTransactionIdAndRiskScoreIdAndPolicyId(UUID transactionId, UUID riskScoreId, UUID policyId);
}
