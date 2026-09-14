package com.sentinelflow.operations;

import java.util.List;
import java.util.Map;

/**
 * Read-only Kafka attempt distribution plus attempts stuck in PROCESSING for
 * longer than the stuck threshold. The stuck list is diagnostic: no automatic
 * mutation is performed on these rows.
 */
public record AttemptSummaryResponse(
        Map<String, Long> statusCounts,
        int stuckThresholdMinutes,
        long stuckCount,
        List<AttemptDto> stuck) {
}