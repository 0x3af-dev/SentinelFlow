package com.sentinelflow.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelflow.kafka.KafkaTopics;
import com.sentinelflow.kafka.TransactionProcessingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "sentinelflow.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, TransactionProcessingEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, TransactionProcessingEvent> kafkaTemplate,
                           ObjectMapper objectMapper,
                           @Value("${sentinelflow.kafka.topic.transactions:" + KafkaTopics.TRANSACTION_PROCESS + "}") String topic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING);
        for (OutboxEvent e : pending) {
            try {
                TransactionProcessingEvent event = mapToEvent(e);
                kafkaTemplate.send(topic, e.getAggregateId(), event).get();
                e.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                repository.save(e);
                log.info("Outbox published eventId={} aggregateId={}", e.getId(), e.getAggregateId());
            } catch (Exception ex) {
                log.warn("Outbox publish failed id={} error={}", e.getId(), ex.getMessage());
                e.incrementRetry();
                if (e.getRetryCount() > 5) {
                    e.setStatus(OutboxEvent.OutboxStatus.FAILED);
                }
                repository.save(e);
            }
        }
    }

    private TransactionProcessingEvent mapToEvent(OutboxEvent e) {
        var p = e.getPayload();
        return new TransactionProcessingEvent(
                (String) p.get("eventId"),
                (String) p.get("eventType"),
                (Integer) p.get("eventVersion"),
                (String) p.get("transactionReference"),
                Instant.parse((String) p.get("occurredAt")),
                (String) p.get("correlationId"),
                (String) p.get("producer"),
                (String) p.get("schemaVersion")
        );
    }
}
