package com.sentinelflow.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlInferenceResponse(
    @JsonProperty("model_name") String modelName,
    @JsonProperty("model_version") String modelVersion,
    @JsonProperty("feature_schema_version") String featureSchemaVersion,
    @JsonProperty("risk_score") double riskScore,
    @JsonProperty("prediction") String prediction,
    @JsonProperty("risk_factors") List<RiskFactorDto> riskFactors,
    @JsonProperty("model_metadata") Map<String, Object> modelMetadata,
    @JsonProperty("inference_latency_ms") long inferenceLatencyMs,
    @JsonProperty("inference_timestamp") String inferenceTimestamp
) {

    public boolean isValid() {
        return riskScore >= 0.0 && riskScore <= 1.0 && modelName != null && modelVersion != null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskFactorDto(
        @JsonProperty("factor_type") String factorType,
        @JsonProperty("description") String description,
        @JsonProperty("severity") String severity,
        @JsonProperty("source") String source,
        @JsonProperty("details") Map<String, Object> details
    ) {}
}