package com.sentinelflow.api;

import com.sentinelflow.kafka.TransactionProcessingEvent;
import com.sentinelflow.kafka.outbox.OutboxEvent;
import com.sentinelflow.kafka.outbox.OutboxService;
import com.sentinelflow.observability.Md;
import com.sentinelflow.transaction.TransactionRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entry point for the asynchronous Kafka flow. Persists a PENDING outbox row in
 * the same transaction as the read, then the outbox relay publishes to Kafka.
 * The accepted correlationId generated here is the root correlation identifier
 * for the whole downstream flow (outbox → Kafka → processor → pipeline).
 */
@RestController
@RequestMapping("/internal/kafka/transactions")
public class TransactionEventEnqueueController {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventEnqueueController.class);

    private final TransactionRepository transactionRepository;
    private final OutboxService outboxService;

    public TransactionEventEnqueueController(TransactionRepository transactionRepository,
                                             OutboxService outboxService) {
        this.transactionRepository = transactionRepository;
        this.outboxService = outboxService;
    }

    @PostMapping("/{transactionReference}/enqueue")
    public ResponseEntity<Map<String, String>> enqueue(@PathVariable String transactionReference) {
        if (transactionRepository.findByTransactionReference(transactionReference).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String correlationId = UUID.randomUUID().toString();
        return Md.run(Md.of(Md.OP_ENQUEUE, correlationId, transactionReference), () -> {
            TransactionProcessingEvent event = TransactionProcessingEvent.create(transactionReference, correlationId);
            OutboxEvent outbox = outboxService.saveTransactionEvent(event);
            log.info("Enqueued transaction event eventId={} transactionReference={} correlationId={} outboxId={}",
                    event.eventId(), transactionReference, correlationId, outbox.getId());
            return ResponseEntity.accepted().body(Map.of(
                    "eventId", event.eventId(),
                    "transactionReference", transactionReference,
                    "correlationId", correlationId,
                    "outboxId", outbox.getId().toString()
            ));
        });
    }
}