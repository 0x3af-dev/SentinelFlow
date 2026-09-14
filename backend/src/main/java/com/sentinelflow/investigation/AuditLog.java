package com.sentinelflow.investigation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "actor_reference", length = 128)
    private String actorReference;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state", columnDefinition = "jsonb")
    private Map<String, Object> previousState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state", columnDefinition = "jsonb")
    private Map<String, Object> newState;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "correlation_reference", length = 64)
    private String correlationReference;

    protected AuditLog() {
    }

    public AuditLog(String actorType, String actorReference, String action,
                    String entityType, UUID entityId, Map<String, Object> previousState,
                    Map<String, Object> newState, Instant timestamp,
                    Map<String, Object> metadata, String correlationReference) {
        this.actorType = actorType;
        this.actorReference = actorReference;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.previousState = previousState;
        this.newState = newState;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.metadata = metadata;
        this.correlationReference = correlationReference;
    }

    public UUID getId() {
        return id;
    }

    public String getActorType() {
        return actorType;
    }

    public String getActorReference() {
        return actorReference;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Map<String, Object> getPreviousState() {
        return previousState;
    }

    public Map<String, Object> getNewState() {
        return newState;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getCorrelationReference() {
        return correlationReference;
    }
}