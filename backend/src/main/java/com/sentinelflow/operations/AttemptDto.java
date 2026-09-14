package com.sentinelflow.operations;

import java.time.Instant;

/**
 * Lightweight Kafka attempt projection for the operations endpoints. Never
 * exposes payload bytes, only aggregate metadata and the last error message.
 */
public record AttemptDto(
        String eventId,
        String transactionReference,
        String correlationId,
        String eventType,
        String status,
        int attemptCount,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {
}