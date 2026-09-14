package com.sentinelflow.risk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {
    List<RiskScore> findByTransactionId(UUID transactionId);

    List<RiskScore> findByTransaction_Id(UUID transactionId);

    @Query("SELECT r FROM RiskScore r WHERE r.transaction.id = :transactionId ORDER BY r.createdAt DESC")
    Optional<RiskScore> findFirstByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
}
