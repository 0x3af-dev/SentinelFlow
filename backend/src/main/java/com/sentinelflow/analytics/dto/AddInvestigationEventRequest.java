package com.sentinelflow.analytics.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AddInvestigationEventRequest(
        String eventType,
        String actorType,
        String actorReference,
        Map<String, Object> payload
) {
}