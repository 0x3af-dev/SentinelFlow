package com.sentinelflow.operations;

import java.time.Instant;
import java.util.Map;

/**
 * Read-only operational snapshot: dependency probes, outbox/DLQ backlogs,
 * Kafka attempt distribution, application counters and timer means.
 * Nothing on this record is persisted and nothing is ever written by its
 * producers.
 */
public record OperationalSummaryResponse(
        Instant generatedAt,
        Map<String, DependencyProbeResult> dependencies,
        OutboxSummary outbox,
        DlqSummary dlq,
        AttemptSummary attempts,
        Counters counters,
        Latency latencyMs) {

    public record OutboxSummary(long pending, long failed, DependencyStatus status) {
    }

    public record DlqSummary(long count) {
    }

    public record AttemptSummary(Map<String, Long> statusCounts, long stuckProcessingCount) {
    }

    public record Counters(
            double transactionProcessed,
            double transactionSucceeded,
            double transactionFailed,
            Map<String, Double> decisionsByType,
            double kafkaConsumed,
            double kafkaSucceeded,
            double kafkaDuplicate,
            double kafkaRetryable,
            double kafkaPermanent,
            double kafkaDeadLettered,
            double mlRequests,
            double mlSuccess,
            double mlFailure,
            double aiRequests,
            double aiSuccess,
            double aiFailure) {
    }

    public record Latency(double transactionMeanMs, double kafkaMeanMs, double mlMeanMs, double aiMeanMs) {
    }
}