package com.sentinelflow.operations;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only outbox row projection for the operations endpoints.
 */
public record OutboxRowDto(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        int retryCount,
        Instant createdAt) {
}