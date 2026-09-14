package com.sentinelflow.ml;

import com.sentinelflow.metrics.SentinelFlowMetrics;
import com.sentinelflow.observability.Md;
import com.sentinelflow.shared.dto.MlPrediction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class MlInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(MlInferenceClient.class);

    private final WebClient webClient;
    private final Duration timeout;
    private final String expectedModelName;
    private final String expectedFeatureSchemaVersion;
    private final SentinelFlowMetrics metrics;

    public MlInferenceClient(
            WebClient.Builder webClientBuilder,
            @Value("${ml.service.url:http://localhost:8001}") String mlServiceUrl,
            @Value("${ml.client.timeout:5000}") int timeoutMs,
            @Value("${ml.expected.model-name:risk-model}") String expectedModelName,
            @Value("${ml.expected.feature-schema-version:fs-v1}") String expectedFeatureSchemaVersion,
            SentinelFlowMetrics metrics) {
        this.webClient = webClientBuilder.baseUrl(mlServiceUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.expectedModelName = expectedModelName;
        this.expectedFeatureSchemaVersion = expectedFeatureSchemaVersion;
        this.metrics = metrics;
    }

    public MlPrediction infer(MlInferenceRequest request) {
        long startedNanos = System.nanoTime();
        metrics.mlRequested();
        return Md.run(Map.of(Md.OPERATION, Md.OP_ML, Md.STATUS, "running"), () -> {
            try {
                MlPrediction prediction = inferInternal(request);
                metrics.mlSucceeded(System.nanoTime() - startedNanos);
                return prediction;
            } catch (MlInferenceException e) {
                metrics.mlFailed(categoryOf(e), System.nanoTime() - startedNanos);
                throw e;
            }
        });
    }

    private MlPrediction inferInternal(MlInferenceRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            MlInferenceResponse response = webClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new MlInferenceException(
                                            "ML service error: " + clientResponse.statusCode() + " - " + body)))
                    .bodyToMono(MlInferenceResponse.class)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                            .filter(throwable -> !(throwable instanceof MlInferenceException))
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();

            if (response == null) {
                throw new MlInferenceException("ML service returned null response");
            }

            validateResponse(response);
            
            long latency = System.currentTimeMillis() - startTime;
            log.info("ML inference completed: model={}, version={}, score={}, latency={}ms",
                    response.modelName(), response.modelVersion(), response.riskScore(), latency);

            return mapToMlPrediction(response, latency);

        } catch (WebClientResponseException e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("ML inference HTTP error: status={}, body={}, latency={}ms",
                    e.getStatusCode(), e.getResponseBodyAsString(), latency);
            throw new MlInferenceException("ML service HTTP error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException || e.getMessage() != null && e.getMessage().contains("Timeout")) {
                long latency = System.currentTimeMillis() - startTime;
                log.error("ML inference timeout after {}ms", latency);
                throw new MlInferenceException("ML inference timeout", e);
            }
            long latency = System.currentTimeMillis() - startTime;
            log.error("ML inference failed: {}, latency={}ms", e.getMessage(), latency);
            throw new MlInferenceException("ML inference failed: " + e.getMessage(), e);
        }
    }

    private void validateResponse(MlInferenceResponse response) {
        if (!response.isValid()) {
            throw new MlInferenceException("Invalid ML response: score out of range or missing model info");
        }

        if (!expectedModelName.equals(response.modelName())) {
            throw new MlInferenceException("Model name mismatch: expected=" + expectedModelName + ", got=" + response.modelName());
        }

        if (!expectedFeatureSchemaVersion.equals(response.featureSchemaVersion())) {
            throw new MlInferenceException("Feature schema version mismatch: expected=" + expectedFeatureSchemaVersion + ", got=" + response.featureSchemaVersion());
        }
    }

    private MlPrediction mapToMlPrediction(MlInferenceResponse response, long latencyMs) {
        List<MlPrediction.RiskFactorDto> factors = response.riskFactors().stream()
                .map(rf -> new MlPrediction.RiskFactorDto(
                        rf.factorType(),
                        rf.description(),
                        rf.severity(),
                        rf.details()
                ))
                .toList();

        return new MlPrediction(
                response.modelName(),
                response.modelVersion(),
                response.featureSchemaVersion(),
                response.riskScore(),
                response.prediction(),
                factors,
                response.modelMetadata(),
                latencyMs,
                Instant.now()
        );
    }

    public static class MlInferenceException extends RuntimeException {
        public MlInferenceException(String message) {
            super(message);
        }

        public MlInferenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Classifies an ML failure for the low-cardinality metric tag. Categories:
     * TIMEOUT, HTTP, UNAVAILABLE, INVALID (malformed/identity mismatch), OTHER.
     * Business semantics are deliberately not encoded here.
     */
    private static String categoryOf(MlInferenceException e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (e.getCause() instanceof TimeoutException || message.contains("timeout")) return "TIMEOUT";
        if (e.getCause() instanceof WebClientResponseException) return "HTTP";
        if (message.contains("http error")) return "HTTP";
        if (message.contains("null response") || message.contains("invalid ml response") || message.contains("mismatch")) {
            return "INVALID";
        }
        if (message.contains("unavailable") || message.contains("503") || message.contains("connection refused")
                || message.contains("connection")) {
            return "UNAVAILABLE";
        }
        return "OTHER";
    }
}