package com.sentinelflow.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evidence_nodes")
public class EvidenceNode {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 32)
    private EvidenceNodeType nodeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private EvidenceSourceType sourceType;

    @Column(name = "entity_type", length = 32)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "observed_at")
    private Instant observedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb")
    private Map<String, Object> value;

    @Column(name = "confidence")
    private Double confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EvidenceNode() {
    }

    public EvidenceNode(EvidenceNodeType nodeType, EvidenceSourceType sourceType,
                        String entityType, UUID entityId, Instant observedAt,
                        Map<String, Object> value, Double confidence,
                        Map<String, Object> metadata) {
        this.nodeType = nodeType;
        this.sourceType = sourceType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.observedAt = observedAt;
        this.value = value;
        this.confidence = confidence;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public EvidenceNodeType getNodeType() {
        return nodeType;
    }

    public EvidenceSourceType getSourceType() {
        return sourceType;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Map<String, Object> getValue() {
        return value;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}