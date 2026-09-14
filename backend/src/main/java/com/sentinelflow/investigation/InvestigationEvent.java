package com.sentinelflow.investigation;

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
@Table(name = "investigation_events")
public class InvestigationEvent {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "actor_reference", length = 128)
    private String actorReference;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvestigationEvent() {
    }

    public InvestigationEvent(Investigation investigation, String eventType,
                              String actorType, String actorReference,
                              Instant eventTimestamp, Map<String, Object> payload) {
        this.investigation = investigation;
        this.eventType = eventType;
        this.actorType = actorType;
        this.actorReference = actorReference;
        this.eventTimestamp = eventTimestamp == null ? Instant.now() : eventTimestamp;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Investigation getInvestigation() {
        return investigation;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActorType() {
        return actorType;
    }

    public String getActorReference() {
        return actorReference;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}