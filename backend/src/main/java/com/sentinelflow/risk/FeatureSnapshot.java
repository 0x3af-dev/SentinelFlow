package com.sentinelflow.risk;

import com.sentinelflow.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "feature_snapshots")
public class FeatureSnapshot {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "feature_schema_version", length = 32)
    private String featureSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private Map<String, Object> features;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FeatureSnapshot() {
    }

    public FeatureSnapshot(Transaction transaction, String featureSchemaVersion,
                           Map<String, Object> features, Instant generatedAt) {
        this.transaction = transaction;
        this.featureSchemaVersion = featureSchemaVersion;
        this.features = features;
        this.generatedAt = generatedAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getFeatureSchemaVersion() {
        return featureSchemaVersion;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
