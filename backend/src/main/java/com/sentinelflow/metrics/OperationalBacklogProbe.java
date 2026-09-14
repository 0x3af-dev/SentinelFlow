package com.sentinelflow.metrics;

import com.sentinelflow.kafka.attempt.KafkaProcessingAttempt;
import com.sentinelflow.kafka.attempt.KafkaProcessingAttemptRepository;
import com.sentinelflow.kafka.outbox.OutboxEvent;
import com.sentinelflow.kafka.outbox.OutboxEventRepository;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the low-cost operational gauges current without placing a database
 * query on every request path. Runs on a conservative interval; the {@link
 * SentinelFlowMetrics} gauges are only ever read at scrape time.
 */
@Component
@EnableScheduling
public class OperationalBacklogProbe {

    private final OutboxEventRepository outboxRepository;
    private final KafkaProcessingAttemptRepository attemptRepository;
    private final SentinelFlowMetrics metrics;

    public OperationalBacklogProbe(OutboxEventRepository outboxRepository,
                                   KafkaProcessingAttemptRepository attemptRepository,
                                   SentinelFlowMetrics metrics) {
        this.outboxRepository = outboxRepository;
        this.attemptRepository = attemptRepository;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${sentinelflow.operations.backlog-interval-ms:15000}")
    public void refresh() {
        metrics.updateOutboxPending(outboxRepository.countByStatus(OutboxEvent.OutboxStatus.PENDING));
        metrics.updateOutboxFailed(outboxRepository.countByStatus(OutboxEvent.OutboxStatus.FAILED));
        metrics.updateKafkaDlqBacklog(attemptRepository.countByStatusIn(
                java.util.Set.of(KafkaProcessingAttempt.AttemptStatus.DEAD_LETTERED,
                        KafkaProcessingAttempt.AttemptStatus.PERMANENT_FAILURE)));
    }
}