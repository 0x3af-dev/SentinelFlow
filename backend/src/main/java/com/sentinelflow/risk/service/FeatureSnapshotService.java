package com.sentinelflow.risk.service;

import com.sentinelflow.risk.FeatureSnapshot;
import com.sentinelflow.risk.FeatureSnapshotRepository;
import com.sentinelflow.shared.dto.FeatureSet;
import com.sentinelflow.transaction.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class FeatureSnapshotService {

    private final FeatureSnapshotRepository repository;

    public FeatureSnapshotService(FeatureSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public FeatureSnapshot create(Transaction transaction, FeatureSet featureSet) {
        FeatureSnapshot snapshot = new FeatureSnapshot(
                transaction,
                featureSet.featureSchemaVersion(),
                featureSet.features(),
                Instant.now()
        );
        return repository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<FeatureSnapshot> findLatestByTransaction(UUID transactionId) {
        return repository.findFirstByTransactionIdOrderByCreatedAtDesc(transactionId);
    }

    @Transactional(readOnly = true)
    public boolean existsForTransactionAndSchema(UUID transactionId, String schemaVersion) {
        return repository.existsByTransaction_IdAndFeatureSchemaVersion(transactionId, schemaVersion);
    }
}