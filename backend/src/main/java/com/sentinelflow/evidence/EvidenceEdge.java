package com.sentinelflow.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "evidence_edges")
public class EvidenceEdge {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_node_id", nullable = false)
    private EvidenceNode sourceNode;

    @ManyToOne(optional = false)
    @JoinColumn(name = "target_node_id", nullable = false)
    private EvidenceNode targetNode;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 32)
    private EvidenceRelationshipType relationshipType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EvidenceEdge() {
    }

    public EvidenceEdge(EvidenceNode sourceNode, EvidenceNode targetNode,
                        EvidenceRelationshipType relationshipType, Map<String, Object> metadata) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.relationshipType = relationshipType;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public EvidenceNode getSourceNode() {
        return sourceNode;
    }

    public EvidenceNode getTargetNode() {
        return targetNode;
    }

    public EvidenceRelationshipType getRelationshipType() {
        return relationshipType;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}