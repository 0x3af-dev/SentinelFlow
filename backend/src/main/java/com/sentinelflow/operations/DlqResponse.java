package com.sentinelflow.operations;

import java.time.Instant;
import java.util.List;

/**
 * Read-only view of the dead-letter backlog: attempts that exhausted retries
 * (PERMANENT_FAILURE) or were dead-lettered (DEAD_LETTERED). Inspection only.
 */
public record DlqResponse(long count, List<AttemptDto> recent) {
}