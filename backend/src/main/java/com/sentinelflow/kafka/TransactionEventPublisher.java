package com.sentinelflow.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer abstraction. Kafka is transport, not business logic.
 */
@Service
@ConditionalOnProperty(name = "sentinelflow.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private final KafkaTemplate<String, TransactionProcessingEvent> kafkaTemplate;
    private final String topic;

    public TransactionEventPublisher(KafkaTemplate<String, TransactionProcessingEvent> kafkaTemplate,
                                     @Value("${sentinelflow.kafka.topic.transactions:" + KafkaTopics.TRANSACTION_PROCESS + "}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, TransactionProcessingEvent>> publish(TransactionProcessingEvent event) {
        event.validate();
        log.info("Publishing event eventId={} transactionReference={} correlationId={} topic={}",
                event.eventId(), event.transactionReference(), event.correlationId(), topic);
        // partition key = transactionReference for ordering per transaction
        return kafkaTemplate.send(topic, event.transactionReference(), event);
    }

    public CompletableFuture<SendResult<String, TransactionProcessingEvent>> publish(String transactionReference, String correlationId) {
        TransactionProcessingEvent event = TransactionProcessingEvent.create(transactionReference, correlationId);
        return publish(event);
    }

    public CompletableFuture<SendResult<String, TransactionProcessingEvent>> publish(String transactionReference) {
        return publish(transactionReference, null);
    }
}
