package com.sentinelflow.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MlPrediction(
    String modelName,
    String modelVersion,
    String featureSchemaVersion,
    Double riskScore,
    String prediction,
    List<RiskFactorDto> riskFactors,
    Map<String, Object> modelMetadata,
    long inferenceLatencyMs,
    Instant inferenceTimestamp
) {

    public record RiskFactorDto(
        String factorType,
        String description,
        String severity,
        Map<String, Object> details
    ) {}

    public boolean isValid() {
        return riskScore != null && riskScore >= 0.0 && riskScore <= 1.0;
    }
}