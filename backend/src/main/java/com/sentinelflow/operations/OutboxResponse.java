package com.sentinelflow.operations;

import java.time.Instant;
import java.util.List;

/**
 * Read-only outbox relay health: pending/failed volumes plus the oldest
 * pending row age (the key symptom of a stalled or failing relay) and the
 * most recent failed rows.
 */
public record OutboxResponse(
        long pending,
        long failed,
        DependencyStatus status,
        Instant oldestPendingCreatedAt,
        long oldestPendingAgeSeconds,
        List<OutboxRowDto> recentFailed) {
}