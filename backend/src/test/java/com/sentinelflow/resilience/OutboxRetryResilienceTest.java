package com.sentinelflow.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelflow.kafka.KafkaTopics;
import com.sentinelflow.kafka.TransactionProcessingEvent;
import com.sentinelflow.kafka.outbox.OutboxEvent;
import com.sentinelflow.kafka.outbox.OutboxEventRepository;
import com.sentinelflow.kafka.outbox.OutboxPublisher;
import com.sentinelflow.metrics.SentinelFlowMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Phase 7 resilience for the outbox relay: transient Kafka outages must keep
 * the row PENDING, increment its retry counter and retry on the next cycle;
 * retries are bounded and the row goes FAILED (terminal) rather than looping
 * forever. No dead-letter content is replayed automatically.
 */
class OutboxRetryResilienceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SentinelFlowMetrics metrics = new SentinelFlowMetrics(registry);
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final KafkaTemplate<String, TransactionProcessingEvent> kafka = mock(KafkaTemplate.class);

    @SuppressWarnings("unchecked")
    private void stubSend(int failures) {
        AtomicInteger remaining = new AtomicInteger(failures);
        when(kafka.send(any(), any(), any())).thenAnswer(inv -> {
            if (remaining.getAndDecrement() > 0) {
                return CompletableFuture.failedFuture(new KafkaException("broker unavailable"));
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private OutboxEvent pendingRow() {
        return new OutboxEvent("transaction", "TXN-RETRY-1", "TransactionProcessingEvent",
                Map.of("eventId", "evt-1", "eventType", "TransactionProcessingEvent", "eventVersion", 1,
                        "transactionReference", "TXN-RETRY-1", "occurredAt", "2026-09-14T10:00:00Z",
                        "correlationId", "corr-1", "producer", "api", "schemaVersion", "1.0"));
    }

    @Test
    void transientKafkaOutageRetriesRowThenPublishes() {
        OutboxEvent row = pendingRow();
        when(repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(List.of(row));
        stubSend(1);

        OutboxPublisher relay = new OutboxPublisher(repository, kafka, new ObjectMapper(), metrics,
                KafkaTopics.TRANSACTION_PROCESS);
        relay.publishPending();
        assertThat(row.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        assertThat(row.getRetryCount()).isEqualTo(1);

        relay.publishPending();
        assertThat(row.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
        assertThat(registry.find("sentinelflow.outbox.published.total").counter().count()).isEqualTo(1);
        assertThat(registry.find("sentinelflow.outbox.retry.total").counter().count()).isEqualTo(1);
    }

    @Test
    void retriesAreBoundedAndExhaustionMarksRowFailed() {
        OutboxEvent row = pendingRow();
        when(repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(List.of(row));
        stubSend(Integer.MAX_VALUE);

        OutboxPublisher relay = new OutboxPublisher(repository, kafka, new ObjectMapper(), metrics,
                KafkaTopics.TRANSACTION_PROCESS);
        for (int i = 0; i < 6; i++) {
            relay.publishPending();
        }

        assertThat(row.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);
        assertThat(row.getRetryCount()).isEqualTo(6);
        assertThat(registry.find("sentinelflow.outbox.failed.total").counter().count()).isEqualTo(1);
        assertThat(registry.find("sentinelflow.outbox.published.total").counter().count()).isEqualTo(0);
    }
}