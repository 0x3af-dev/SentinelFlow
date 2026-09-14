package com.sentinelflow.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight, read-only transaction overview for the investigation UI. The
 * decided flag reports whether a persisted decision record exists for the
 * transaction; it never computes or implies a decision.
 */
public record TransactionOverview(
        String transactionReference,
        BigDecimal amount,
        String currency,
        String channel,
        String transactionType,
        String status,
        Instant transactionTimestamp,
        Instant createdAt,
        boolean decided
) {
}