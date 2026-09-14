package com.sentinelflow.analytics.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One entry in an investigation's append-only timeline.
 */
public record TimelineEntry(
        UUID eventId,
        String eventType,
        String actorType,
        String actorReference,
        Instant eventTimestamp,
        Map<String, Object> payload
) {
}