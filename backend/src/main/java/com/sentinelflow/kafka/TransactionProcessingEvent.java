package com.sentinelflow.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical transaction processing event for Kafka.
 * Versioned, validated, deterministic.
 */
public record TransactionProcessingEvent(
        @JsonProperty("eventId")
        @NotBlank String eventId,

        @JsonProperty("eventType")
        @NotBlank String eventType,

        @JsonProperty("eventVersion")
        @NotNull Integer eventVersion,

        @JsonProperty("transactionReference")
        @NotBlank String transactionReference,

        @JsonProperty("occurredAt")
        @NotNull Instant occurredAt,

        @JsonProperty("correlationId")
        String correlationId,

        @JsonProperty("producer")
        String producer,

        @JsonProperty("schemaVersion")
        String schemaVersion
) {
    public static final String TYPE_TRANSACTION_PROCESS = "TRANSACTION_PROCESS";
    public static final int CURRENT_VERSION = 1;
    public static final String SCHEMA_VERSION = "v1";

    public static TransactionProcessingEvent create(String transactionReference, String correlationId) {
        return new TransactionProcessingEvent(
                UUID.randomUUID().toString(),
                TYPE_TRANSACTION_PROCESS,
                CURRENT_VERSION,
                transactionReference,
                Instant.now(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                "sentinelflow-backend",
                SCHEMA_VERSION
        );
    }

    public static TransactionProcessingEvent create(String transactionReference) {
        return create(transactionReference, null);
    }

    public void validate() {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId missing");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType missing");
        if (!TYPE_TRANSACTION_PROCESS.equals(eventType)) throw new IllegalArgumentException("unsupported eventType: " + eventType);
        if (eventVersion == null || eventVersion != CURRENT_VERSION) throw new IllegalArgumentException("unsupported eventVersion: " + eventVersion);
        if (transactionReference == null || transactionReference.isBlank()) throw new IllegalArgumentException("transactionReference missing");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt missing");
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId missing");
    }
}
