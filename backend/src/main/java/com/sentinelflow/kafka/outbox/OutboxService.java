package com.sentinelflow.kafka.outbox;

import com.sentinelflow.kafka.TransactionProcessingEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;

    public OutboxService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OutboxEvent saveTransactionEvent(TransactionProcessingEvent event) {
        Map<String, Object> payload = Map.of(
                "eventId", event.eventId(),
                "eventType", event.eventType(),
                "eventVersion", event.eventVersion(),
                "transactionReference", event.transactionReference(),
                "occurredAt", event.occurredAt().toString(),
                "correlationId", event.correlationId(),
                "producer", event.producer(),
                "schemaVersion", event.schemaVersion()
        );
        OutboxEvent outbox = new OutboxEvent("TRANSACTION", event.transactionReference(), event.eventType(), payload);
        return repository.save(outbox);
    }
}
