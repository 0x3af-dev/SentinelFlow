package com.sentinelflow.operations;

import com.sentinelflow.kafka.attempt.KafkaProcessingAttempt;
import com.sentinelflow.kafka.attempt.KafkaProcessingAttemptRepository;
import com.sentinelflow.kafka.outbox.OutboxEvent;
import com.sentinelflow.kafka.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Aggregates dependency probes, metric snapshots and read-model counts into the
 * read-only operational summary served to the System Health UI. Never writes:
 * all inputs come from probes, meters and SELECTs.
 */
@Service
public class OperationalService {

    private static final String TRANSACTION_SUCCESS = "sentinelflow.transaction.processing.success.total";
    private static final String TRANSACTION_FAILURE = "sentinelflow.transaction.processing.failure.total";
    private static final String TRANSACTION_DECISIONS = "sentinelflow.transaction.decision.total";
    private static final String TRANSACTION_DURATION = "sentinelflow.transaction.processing.duration";
    private static final String KAFKA_CONSUMED = "sentinelflow.kafka.events.consumed.total";
    private static final String KAFKA_SUCCEEDED = "sentinelflow.kafka.events.succeeded.total";
    private static final String KAFKA_DUPLICATE = "sentinelflow.kafka.events.duplicate.total";
    private static final String KAFKA_RETRYABLE = "sentinelflow.kafka.events.retryable.total";
    private static final String KAFKA_PERMANENT = "sentinelflow.kafka.events.permanent.total";
    private static final String KAFKA_DEAD_LETTER = "sentinelflow.kafka.events.dead_lettered.total";
    private static final String KAFKA_DURATION = "sentinelflow.kafka.processing.duration";
    private static final String ML_REQUESTS = "sentinelflow.ml.requests.total";
    private static final String ML_SUCCESS = "sentinelflow.ml.success.total";
    private static final String ML_FAILURE = "sentinelflow.ml.failure.total";
    private static final String ML_DURATION = "sentinelflow.ml.duration";
    private static final String AI_TOTAL = "sentinelflow.ai.investigation.total";
    private static final String AI_SUCCESS = "sentinelflow.ai.investigation.success.total";
    private static final String AI_FAILURE = "sentinelflow.ai.investigation.failure.total";
    private static final String AI_DURATION = "sentinelflow.ai.investigation.duration";

    private static final long PENDING_BACKLOG_ALERT = 100;

    private final OperationalProbeService probes;
    private final OutboxEventRepository outboxRepository;
    private final KafkaProcessingAttemptRepository attemptRepository;
    private final MeterRegistry meterRegistry;
    private final DataIntegrityService integrityService;
    private final int stuckThresholdMinutes;

    public OperationalService(
            OperationalProbeService probes,
            OutboxEventRepository outboxRepository,
            KafkaProcessingAttemptRepository attemptRepository,
            MeterRegistry meterRegistry,
            DataIntegrityService integrityService,
            @Value("${sentinelflow.operations.stuck-threshold-minutes:15}") int stuckThresholdMinutes) {
        this.probes = probes;
        this.outboxRepository = outboxRepository;
        this.attemptRepository = attemptRepository;
        this.meterRegistry = meterRegistry;
        this.integrityService = integrityService;
        this.stuckThresholdMinutes = stuckThresholdMinutes;
    }

    public OperationalSummaryResponse summary() {
        long pending = outboxRepository.countByStatus(OutboxEvent.OutboxStatus.PENDING);
        long failed = outboxRepository.countByStatus(OutboxEvent.OutboxStatus.FAILED);
        long dlqCount = attemptRepository.countByStatusIn(List.of(
                KafkaProcessingAttempt.AttemptStatus.DEAD_LETTERED,
                KafkaProcessingAttempt.AttemptStatus.PERMANENT_FAILURE));
        long stuck = stuckCount();

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (KafkaProcessingAttempt.AttemptStatus status : KafkaProcessingAttempt.AttemptStatus.values()) {
            statusCounts.put(status.name(), attemptRepository.countByStatus(status));
        }

        return new OperationalSummaryResponse(
                Instant.now(),
                Map.of(
                        "postgres", probes.postgres(),
                        "kafka", probes.kafka(),
                        "ml", probes.ml(),
                        "ai", probes.ai()),
                new OperationalSummaryResponse.OutboxSummary(pending, failed, outboxStatus(pending, failed)),
                new OperationalSummaryResponse.DlqSummary(dlqCount),
                new OperationalSummaryResponse.AttemptSummary(statusCounts, stuck),
                counters(),
                latencyMs());
    }

    private long stuckCount() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(stuckThresholdMinutes));
        return attemptRepository.countByStatusAndUpdatedAtBefore(KafkaProcessingAttempt.AttemptStatus.PROCESSING, cutoff);
    }

    public DlqResponse dlq() {
        List<KafkaProcessingAttempt> recent = attemptRepository.findTop20ByStatusInOrderByUpdatedAtDesc(List.of(
                KafkaProcessingAttempt.AttemptStatus.DEAD_LETTERED,
                KafkaProcessingAttempt.AttemptStatus.PERMANENT_FAILURE));
        long count = attemptRepository.countByStatusIn(List.of(
                KafkaProcessingAttempt.AttemptStatus.DEAD_LETTERED,
                KafkaProcessingAttempt.AttemptStatus.PERMANENT_FAILURE));
        return new DlqResponse(count, recent.stream().map(OperationalService::toAttemptDto).toList());
    }

    public OutboxResponse outbox() {
        long pending = outboxRepository.countByStatus(OutboxEvent.OutboxStatus.PENDING);
        long failed = outboxRepository.countByStatus(OutboxEvent.OutboxStatus.FAILED);
        OutboxEvent oldest = outboxRepository.findFirstByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING).orElse(null);
        long oldestAgeSeconds = oldest == null ? 0 : Duration.between(oldest.getCreatedAt(), Instant.now()).toSeconds();
        List<OutboxEvent> recentFailed = outboxRepository.findTop20ByStatusOrderByCreatedAtDesc(OutboxEvent.OutboxStatus.FAILED);
        return new OutboxResponse(
                pending, failed, outboxStatus(pending, failed),
                oldest == null ? null : oldest.getCreatedAt(), oldestAgeSeconds,
                recentFailed.stream().map(OperationalService::toOutboxDto).toList());
    }

    public AttemptSummaryResponse attempts() {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (KafkaProcessingAttempt.AttemptStatus status : KafkaProcessingAttempt.AttemptStatus.values()) {
            statusCounts.put(status.name(), attemptRepository.countByStatus(status));
        }
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(stuckThresholdMinutes));
        List<KafkaProcessingAttempt> stuck = attemptRepository.findTop20ByStatusAndUpdatedAtBefore(
                KafkaProcessingAttempt.AttemptStatus.PROCESSING, cutoff);
        return new AttemptSummaryResponse(statusCounts, stuckThresholdMinutes, stuck.size(),
                stuck.stream().map(OperationalService::toAttemptDto).toList());
    }

    public DataIntegrityService.IntegrityResponse integrity() {
        return integrityService.report();
    }

    private DependencyStatus outboxStatus(long pending, long failed) {
        if (failed > 0) {
            return DependencyStatus.DEGRADED;
        }
        if (pending > PENDING_BACKLOG_ALERT) {
            return DependencyStatus.DEGRADED;
        }
        return DependencyStatus.HEALTHY;
    }

    private OperationalSummaryResponse.Counters counters() {
        Map<String, Double> byDecision = new LinkedHashMap<>();
        Collection<Meter> decisionMeters = meterRegistry.find(TRANSACTION_DECISIONS).meters();
        for (Meter meter : decisionMeters) {
            String decision = meter.getId().getTag("decision");
            if (decision == null || decision.isBlank()) {
                continue;
            }
            byDecision.put(decision, ((Counter) meter).count());
        }
        return new OperationalSummaryResponse.Counters(
                counterValue(TRANSACTION_SUCCESS) + counterValue(TRANSACTION_FAILURE),
                counterValue(TRANSACTION_SUCCESS),
                counterValue(TRANSACTION_FAILURE),
                byDecision,
                counterValue(KAFKA_CONSUMED),
                counterValue(KAFKA_SUCCEEDED),
                counterValue(KAFKA_DUPLICATE),
                counterValue(KAFKA_RETRYABLE),
                counterValue(KAFKA_PERMANENT),
                counterValue(KAFKA_DEAD_LETTER),
                counterValue(ML_REQUESTS),
                counterValue(ML_SUCCESS),
                counterValue(ML_FAILURE),
                counterValue(AI_TOTAL),
                counterValue(AI_SUCCESS),
                counterValue(AI_FAILURE));
    }

    private OperationalSummaryResponse.Latency latencyMs() {
        return new OperationalSummaryResponse.Latency(
                timerMeanMs(TRANSACTION_DURATION),
                timerMeanMs(KAFKA_DURATION),
                timerMeanMs(ML_DURATION),
                timerMeanMs(AI_DURATION));
    }

    private double counterValue(String name) {
        double total = 0;
        for (Meter meter : meterRegistry.find(name).meters()) {
            if (meter instanceof Counter counter) {
                total += counter.count();
            }
        }
        return total;
    }

    private double timerMeanMs(String name) {
        Timer timer = meterRegistry.find(name).timer();
        return timer == null || timer.count() == 0 ? 0 : timer.mean(TimeUnit.MILLISECONDS);
    }

    private static AttemptDto toAttemptDto(KafkaProcessingAttempt a) {
        return new AttemptDto(a.getEventId(), a.getTransactionReference(), a.getCorrelationId(), a.getEventType(),
                a.getStatus().name(), a.getAttemptCount(), a.getLastError(), a.getCreatedAt(), a.getUpdatedAt());
    }

    private static OutboxRowDto toOutboxDto(OutboxEvent e) {
        return new OutboxRowDto(e.getId(), e.getAggregateType(), e.getAggregateId(), e.getEventType(),
                e.getRetryCount(), e.getCreatedAt());
    }
}