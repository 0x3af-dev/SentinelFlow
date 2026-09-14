package com.sentinelflow.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PipelineResult(
    String transactionReference,
    String status,
    String featureSnapshotReference,
    String modelVersion,
    Double riskScore,
    List<Map<String, Object>> riskFactors,
    String decision,
    String decisionReason,
    String policyVersion,
    Instant decisionTimestamp,
    String evidenceId,
    Instant pipelineStartTimestamp,
    Instant pipelineEndTimestamp
) {
}