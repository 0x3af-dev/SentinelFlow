package com.sentinelflow.kafka;

import com.sentinelflow.kafka.attempt.KafkaProcessingAttempt;
import com.sentinelflow.kafka.attempt.KafkaProcessingAttemptRepository;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.pipeline.TransactionIntelligencePipeline;
import com.sentinelflow.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Processing coordinator. Validates the event, tracks attempts idempotently, and
 * delegates the actual business work to the single source of truth:
 * {@link TransactionIntelligencePipeline}. Kafka is transport only — no business
 * logic lives here.
 *
 * <p>Deliberately NOT {@code @Transactional}: the Phase 2 pipeline runs the ML HTTP
 * call outside any database transaction, and that invariant is preserved here.
 * Each attempt status update is its own short transaction ({@code saveAndFlush}).
 */
@Service
public class TransactionEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProcessor.class);

    static final int MAX_RETRIES = 5;

    private final KafkaProcessingAttemptRepository attemptRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionIntelligencePipeline pipeline;

    public TransactionEventProcessor(KafkaProcessingAttemptRepository attemptRepository,
                                     TransactionRepository transactionRepository,
                                     TransactionIntelligencePipeline pipeline) {
        this.attemptRepository = attemptRepository;
        this.transactionRepository = transactionRepository;
        this.pipeline = pipeline;
    }

    public enum ProcessingOutcome {
        SUCCEEDED, DUPLICATE, RETRYABLE_FAILURE, PERMANENT_FAILURE
    }

    public record ProcessingResult(ProcessingOutcome outcome, String reason, String eventId) {}

    public ProcessingResult process(TransactionProcessingEvent event) {
        String eventId = event != null ? event.eventId() : "null";
        String correlationId = event != null ? event.correlationId() : "null";
        String transactionReference = event != null ? event.transactionReference() : "null";
        MDC.put("eventId", eventId);
        MDC.put("correlationId", correlationId);
        MDC.put("transactionReference", transactionReference);
        try {
            log.info("Processing event eventId={} transactionReference={} correlationId={}",
                    eventId, transactionReference, correlationId);

            try {
                event.validate();
            } catch (IllegalArgumentException e) {
                log.warn("Permanent failure: invalid event eventId={} error={}", eventId, e.getMessage());
                saveAttempt(event, KafkaProcessingAttempt.AttemptStatus.PERMANENT_FAILURE, e.getMessage());
                return new ProcessingResult(ProcessingOutcome.PERMANENT_FAILURE, e.getMessage(), eventId);
            }

            final KafkaProcessingAttempt attempt;
            try {
                attempt = trackAttempt(event);
            } catch (AlreadySucceededException e) {
                log.info("Duplicate event already succeeded eventId={}", eventId);
                return new ProcessingResult(ProcessingOutcome.DUPLICATE, "already succeeded", eventId);
            }

            if (transactionRepository.findByTransactionReference(transactionReference).isEmpty()) {
                String msg = "Transaction not found: " + transactionReference;
                log.warn("Retryable failure: {} eventId={}", msg, eventId);
                return markFailure(eventId, attempt, true, msg);
            }

            try {
                var result = pipeline.process(transactionReference);
                log.info("Pipeline succeeded eventId={} transactionReference={} decision={} riskScore={} correlationId={}",
                        eventId, transactionReference, result.decision(), result.riskScore(), correlationId);
                attempt.setStatus(KafkaProcessingAttempt.AttemptStatus.SUCCEEDED);
                attemptRepository.saveAndFlush(attempt);
                return new ProcessingResult(ProcessingOutcome.SUCCEEDED, "pipeline succeeded", eventId);
            } catch (TransactionIntelligencePipeline.PipelineException e) {
                boolean retryable = isRetryable(e.getCause() != null ? e.getCause() : e, e.getMessage());
                return markFailure(eventId, attempt, retryable, e.getMessage());
            }
        } finally {
            MDC.clear();
        }
    }

    private KafkaProcessingAttempt trackAttempt(TransactionProcessingEvent event) {
        Optional<KafkaProcessingAttempt> existing = attemptRepository.findByEventId(event.eventId());
        if (existing.isPresent()) {
            KafkaProcessingAttempt prev = existing.get();
            if (prev.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED) {
                throw new AlreadySucceededException(event.eventId());
            }
            prev.incrementAttempt();
            prev.setStatus(KafkaProcessingAttempt.AttemptStatus.PROCESSING);
            return attemptRepository.saveAndFlush(prev);
        }
        KafkaProcessingAttempt created = new KafkaProcessingAttempt(
                event.eventId(), event.transactionReference(), event.correlationId(),
                event.eventType(), event.eventVersion(), KafkaProcessingAttempt.AttemptStatus.PROCESSING);
        try {
            return attemptRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            // Rare: duplicate events raced on first insert. The winner's row now exists.
            return attemptRepository.findByEventId(event.eventId())
                    .map(prev -> {
                        prev.incrementAttempt();
                        prev.setStatus(KafkaProcessingAttempt.AttemptStatus.PROCESSING);
                        return attemptRepository.saveAndFlush(prev);
                    })
                    .orElseThrow(() -> e);
        }
    }

    private ProcessingResult markFailure(String eventId, KafkaProcessingAttempt attempt, boolean retryable, String msg) {
        if (retryable) {
            if (attempt.getAttemptCount() >= MAX_RETRIES) {
                log.warn("Exhausted {} retries eventId={} error={} -> dead lettering", MAX_RETRIES, eventId, msg);
                attempt.setStatus(KafkaProcessingAttempt.AttemptStatus.DEAD_LETTERED);
                attemptRepository.saveAndFlush(attempt);
                return new ProcessingResult(ProcessingOutcome.PERMANENT_FAILURE, "max retries exceeded: " + msg, eventId);
            }
            log.warn("Retryable failure attempt={}/{} eventId={} error={}", attempt.getAttemptCount(), MAX_RETRIES, eventId, msg);
            attempt.setStatus(KafkaProcessingAttempt.AttemptStatus.RETRYABLE_FAILURE);
            attempt.setLastError(msg);
            attemptRepository.saveAndFlush(attempt);
            return new ProcessingResult(ProcessingOutcome.RETRYABLE_FAILURE, msg, eventId);
        }
        log.warn("Permanent failure eventId={} error={} -> dead lettering", eventId, msg);
        attempt.setStatus(KafkaProcessingAttempt.AttemptStatus.PERMANENT_FAILURE);
        attempt.setLastError(msg);
        attemptRepository.saveAndFlush(attempt);
        return new ProcessingResult(ProcessingOutcome.PERMANENT_FAILURE, msg, eventId);
    }

    private void saveAttempt(TransactionProcessingEvent event, KafkaProcessingAttempt.AttemptStatus status, String error) {
        KafkaProcessingAttempt attempt = new KafkaProcessingAttempt(
                event.eventId(), event.transactionReference(), event.correlationId(),
                event.eventType(), event.eventVersion(), status);
        if (error != null) attempt.setLastError(error);
        attemptRepository.saveAndFlush(attempt);
    }

    private boolean isRetryable(Throwable cause, String msg) {
        if (cause instanceof DataAccessException) return true;
        if (cause instanceof MlInferenceClient.MlInferenceException) {
            String m = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
            if (m.contains("timeout") || m.contains("503") || m.contains("connection") || m.contains("unavailable")) return true;
            return false;
        }
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("timeout") || lower.contains("connection") || lower.contains("unavailable")) return true;
            if (lower.contains("malformed") || lower.contains("invalid") || lower.contains("mismatch")) return false;
        }
        return false;
    }

    static class AlreadySucceededException extends RuntimeException {
        AlreadySucceededException(String eventId) {
            super("Event already succeeded: " + eventId);
        }
    }
}