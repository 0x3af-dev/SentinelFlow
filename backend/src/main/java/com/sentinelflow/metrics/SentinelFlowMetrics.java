package com.sentinelflow.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * SentinelFlow application metrics. Every gauge, counter and timer here is a
 * deliberately low-cardinality operational signal, backed by the Micrometer
 * registry that Spring Boot Actuator configures. High-cardinality identifiers
 * (transactionReference, userId, eventId, investigationId) are NEVER used as
 * metric labels; they live in structured logs and audit storage instead.
 *
 * <p>Naming maps one-to-one onto the Phase 7 operational vocabulary
 * (transaction_processing_total, kafka_dlq_total, outbox_backlog, ...) using
 * the Micrometer dotted convention. See docs/operations/OBSERVABILITY.md.
 *
 * <p>This component is always present and all methods are safe no-ops for
 * any given meter; it simply records into whatever registry Actuator created.
 */
@Component
public class SentinelFlowMetrics {

    private final MeterRegistry registry;

    private final Counter transactionTotal;
    private final Counter transactionSuccess;
    private final Counter transactionFailure;
    private final Counter transactionDecision;
    private final Timer transactionDuration;

    private final Counter kafkaConsumed;
    private final Counter kafkaSucceeded;
    private final Counter kafkaDuplicate;
    private final Counter kafkaRetryable;
    private final Counter kafkaPermanent;
    private final Counter kafkaDeadLettered;
    private final Timer kafkaProcessingDuration;

    /** Outbox backlog gauges are driven by a scheduled probe, never per request. */
    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxFailed = new AtomicLong();
    private final AtomicLong kafkaDlqBacklog = new AtomicLong();
    private final Counter outboxPublished;
    private final Counter outboxFailedTotal;
    private final Counter outboxRetry;
    private final Timer outboxPublishDuration;

    private final Counter mlRequests;
    private final Counter mlSuccess;
    private final Counter mlFailure;
    private final Timer mlDuration;

    private final Counter aiRequests;
    private final Counter aiSuccess;
    private final Counter aiFailure;
    private final Timer aiDuration;
    private final Counter aiToolCalls;

    public SentinelFlowMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.transactionTotal = Counter.builder("sentinelflow.transaction.processing.total")
                .description("Transaction pipeline invocations")
                .register(registry);
        this.transactionSuccess = Counter.builder("sentinelflow.transaction.processing.success.total")
                .description("Transactions that completed the full pipeline")
                .register(registry);
        this.transactionFailure = Counter.builder("sentinelflow.transaction.processing.failure.total")
                .description("Pipeline failures by stage")
                .register(registry);
        this.transactionDecision = Counter.builder("sentinelflow.transaction.decision.total")
                .description("Persisted final decisions by type")
                .register(registry);
        this.transactionDuration = Timer.builder("sentinelflow.transaction.processing.duration")
                .description("End-to-end transaction pipeline latency")
                .register(registry);

        this.kafkaConsumed = Counter.builder("sentinelflow.kafka.events.consumed.total")
                .description("Events handed to the processor")
                .register(registry);
        this.kafkaSucceeded = Counter.builder("sentinelflow.kafka.events.succeeded.total")
                .description("Events processed successfully")
                .register(registry);
        this.kafkaDuplicate = Counter.builder("sentinelflow.kafka.events.duplicate.total")
                .description("Duplicate deliveries already succeeded")
                .register(registry);
        this.kafkaRetryable = Counter.builder("sentinelflow.kafka.events.retryable.total")
                .description("Events routed to the retry topic")
                .register(registry);
        this.kafkaPermanent = Counter.builder("sentinelflow.kafka.events.permanent.total")
                .description("Events failed permanently")
                .register(registry);
        this.kafkaDeadLettered = Counter.builder("sentinelflow.kafka.events.dead_lettered.total")
                .description("Events dead-lettered after exhausting retries or on permanent failure")
                .register(registry);
        this.kafkaProcessingDuration = Timer.builder("sentinelflow.kafka.processing.duration")
                .description("Per-event processing latency (processor path)")
                .register(registry);

        this.outboxPublished = Counter.builder("sentinelflow.outbox.published.total")
                .description("Outbox rows published to Kafka")
                .register(registry);
        this.outboxFailedTotal = Counter.builder("sentinelflow.outbox.failed.total")
                .description("Outbox rows marked FAILED after retries exhausted")
                .register(registry);
        this.outboxRetry = Counter.builder("sentinelflow.outbox.retry.total")
                .description("Outbox publish retries (transient failures)")
                .register(registry);
        this.outboxPublishDuration = Timer.builder("sentinelflow.outbox.publish.duration")
                .description("Time to publish one outbox row to Kafka")
                .register(registry);
        Gauge.builder("sentinelflow.outbox.pending.count", outboxPending, AtomicLong::get)
                .description("Current PENDING outbox rows (backlog)")
                .register(registry);
        Gauge.builder("sentinelflow.outbox.failed.count", outboxFailed, AtomicLong::get)
                .description("Current FAILED outbox rows")
                .register(registry);
        Gauge.builder("sentinelflow.kafka.dlq.backlog.count", kafkaDlqBacklog, AtomicLong::get)
                .description("Current dead-lettered / permanently failed attempts")
                .register(registry);

        this.mlRequests = Counter.builder("sentinelflow.ml.requests.total")
                .description("ML inference requests")
                .register(registry);
        this.mlSuccess = Counter.builder("sentinelflow.ml.success.total")
                .description("Successful ML inferences")
                .register(registry);
        this.mlFailure = Counter.builder("sentinelflow.ml.failure.total")
                .description("ML failures by category")
                .register(registry);
        this.mlDuration = Timer.builder("sentinelflow.ml.duration")
                .description("ML inference latency")
                .register(registry);

        this.aiRequests = Counter.builder("sentinelflow.ai.investigation.total")
                .description("AI investigation requests")
                .register(registry);
        this.aiSuccess = Counter.builder("sentinelflow.ai.investigation.success.total")
                .description("Validated AI explanations produced")
                .register(registry);
        this.aiFailure = Counter.builder("sentinelflow.ai.investigation.failure.total")
                .description("AI investigation failures by code")
                .register(registry);
        this.aiDuration = Timer.builder("sentinelflow.ai.investigation.duration")
                .description("AI investigation latency")
                .register(registry);
        this.aiToolCalls = Counter.builder("sentinelflow.ai.toolcalls.total")
                .description("Budgeted read-only tool calls issued across AI runs")
                .register(registry);
    }

    // ---------------------------------------------------------------- gauges

    public void updateOutboxPending(long pending) {
        outboxPending.set(pending);
    }

    public void updateOutboxFailed(long failed) {
        outboxFailed.set(failed);
    }

    public void updateKafkaDlqBacklog(long backlog) {
        kafkaDlqBacklog.set(backlog);
    }

    public AtomicLong outboxPendingGauge() {
        return outboxPending;
    }

    public AtomicLong outboxFailedGauge() {
        return outboxFailed;
    }

    public AtomicLong kafkaDlqBacklogGauge() {
        return kafkaDlqBacklog;
    }

    // ------------------------------------------------------------ transaction

    public void transactionProcessingStarted() {
        transactionTotal.increment();
    }

    public void transactionProcessingSucceeded(String decision) {
        transactionSuccess.increment();
        if (decision != null && !decision.isBlank()) {
            transactionDecision.increment();
            registry.counter("sentinelflow.transaction.decision.total", "decision", decision).increment();
        }
    }

    public void transactionProcessingFailed(String stage) {
        transactionFailure.increment();
        registry.counter("sentinelflow.transaction.processing.failure.total", "stage", stage).increment();
    }

    public Timer.Sample transactionProcessingSample() {
        return Timer.start(registry);
    }

    public void stopTransactionProcessing(Timer.Sample sample) {
        sample.stop(transactionDuration);
    }

    // ------------------------------------------------------------------ kafka

    public void kafkaConsumed() {
        kafkaConsumed.increment();
    }

    public void kafkaSucceeded() {
        kafkaSucceeded.increment();
    }

    public void kafkaDuplicate() {
        kafkaDuplicate.increment();
    }

    public void kafkaRetryable() {
        kafkaRetryable.increment();
    }

    public void kafkaPermanent() {
        kafkaPermanent.increment();
    }

    public void kafkaDeadLettered() {
        kafkaDeadLettered.increment();
    }

    public Timer.Sample kafkaProcessingSample() {
        return Timer.start(registry);
    }

    public void stopKafkaProcessing(Timer.Sample sample) {
        sample.stop(kafkaProcessingDuration);
    }

    // ----------------------------------------------------------------- outbox

    public void outboxPublished() {
        outboxPublished.increment();
    }

    public void outboxFailed() {
        outboxFailedTotal.increment();
    }

    public void outboxRetried() {
        outboxRetry.increment();
    }

    public Timer.Sample outboxPublishSample() {
        return Timer.start(registry);
    }

    public void stopOutboxPublish(Timer.Sample sample) {
        sample.stop(outboxPublishDuration);
    }

    // --------------------------------------------------------------------- ml

    public void mlRequested() {
        mlRequests.increment();
    }

    public void mlSucceeded(long latencyNanos) {
        mlSuccess.increment();
        mlDuration.record(latencyNanos, TimeUnit.NANOSECONDS);
    }

    public void mlFailed(String category, long latencyNanos) {
        mlFailure.increment();
        registry.counter("sentinelflow.ml.failure.total", "type", category).increment();
        mlDuration.record(latencyNanos, TimeUnit.NANOSECONDS);
    }

    // --------------------------------------------------------------------- ai

    public void aiRequested() {
        aiRequests.increment();
    }

    public void aiSucceeded(long latencyNanos, int toolCalls) {
        aiSuccess.increment();
        aiDuration.record(latencyNanos, TimeUnit.NANOSECONDS);
        if (toolCalls > 0) {
            aiToolCalls.increment(toolCalls);
        }
    }

    public void aiFailed(String code, long latencyNanos) {
        aiFailure.increment();
        registry.counter("sentinelflow.ai.investigation.failure.total", "code", code).increment();
        aiDuration.record(latencyNanos, TimeUnit.NANOSECONDS);
    }
}