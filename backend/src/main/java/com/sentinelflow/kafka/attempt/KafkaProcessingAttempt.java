package com.sentinelflow.kafka.attempt;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "kafka_processing_attempts", uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
public class KafkaProcessingAttempt {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "transaction_reference", nullable = false)
    private String transactionReference;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttemptStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KafkaProcessingAttempt() {}

    public KafkaProcessingAttempt(String eventId, String transactionReference, String correlationId,
                                  String eventType, Integer eventVersion, AttemptStatus status) {
        this.eventId = eventId;
        this.transactionReference = transactionReference;
        this.correlationId = correlationId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public String getTransactionReference() { return transactionReference; }
    public String getCorrelationId() { return correlationId; }
    public String getEventType() { return eventType; }
    public Integer getEventVersion() { return eventVersion; }
    public AttemptStatus getStatus() { return status; }
    public void setStatus(AttemptStatus s) { this.status = s; this.updatedAt = Instant.now(); }
    public Integer getAttemptCount() { return attemptCount; }
    public void incrementAttempt() { this.attemptCount++; this.updatedAt = Instant.now(); }
    public String getLastError() { return lastError; }
    public void setLastError(String e) { this.lastError = e; this.updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public enum AttemptStatus {
        RECEIVED, PROCESSING, SUCCEEDED, RETRYABLE_FAILURE, PERMANENT_FAILURE, DEAD_LETTERED
    }
}
