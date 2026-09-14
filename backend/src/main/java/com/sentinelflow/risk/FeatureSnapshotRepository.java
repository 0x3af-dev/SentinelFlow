package com.sentinelflow.risk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeatureSnapshotRepository extends JpaRepository<FeatureSnapshot, UUID> {
    List<FeatureSnapshot> findByTransactionId(UUID transactionId);

    List<FeatureSnapshot> findByTransaction_Id(UUID transactionId);

    @Query("SELECT f FROM FeatureSnapshot f WHERE f.transaction.id = :transactionId ORDER BY f.createdAt DESC")
    Optional<FeatureSnapshot> findFirstByTransactionIdOrderByCreatedAtDesc(UUID transactionId);

    boolean existsByTransactionIdAndFeatureSchemaVersion(UUID transactionId, String featureSchemaVersion);

    boolean existsByTransaction_IdAndFeatureSchemaVersion(UUID transactionId, String featureSchemaVersion);
}
