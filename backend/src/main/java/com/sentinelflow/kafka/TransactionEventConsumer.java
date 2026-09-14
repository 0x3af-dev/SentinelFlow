package com.sentinelflow.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Thin Kafka consumers. They only route events; business processing happens in
 * {@link TransactionEventProcessor}. Retryable failures are routed to the retry
 * topic (bounded by the attempts table), permanent failures and exhausted retries
 * go to the DLQ.
 */
@Component
@ConditionalOnProperty(name = "sentinelflow.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final TransactionEventProcessor processor;
    private final KafkaTemplate<String, TransactionProcessingEvent> kafkaTemplate;
    private final String dlqTopic;
    private final String retryTopic;

    public TransactionEventConsumer(TransactionEventProcessor processor,
                                    KafkaTemplate<String, TransactionProcessingEvent> kafkaTemplate,
                                    @Value("${sentinelflow.kafka.topic.dlq:" + KafkaTopics.TRANSACTION_PROCESS_DLQ + "}") String dlqTopic,
                                    @Value("${sentinelflow.kafka.topic.retry:" + KafkaTopics.TRANSACTION_PROCESS_RETRY + "}") String retryTopic) {
        this.processor = processor;
        this.kafkaTemplate = kafkaTemplate;
        this.dlqTopic = dlqTopic;
        this.retryTopic = retryTopic;
    }

    @KafkaListener(topics = "${sentinelflow.kafka.topic.transactions:" + KafkaTopics.TRANSACTION_PROCESS + "}",
            groupId = "${sentinelflow.kafka.consumer.group-id:sentinelflow-risk-workers}",
            containerFactory = "kafkaListenerContainerFactory")
    public void listenMain(ConsumerRecord<String, TransactionProcessingEvent> record, Acknowledgment ack) {
        TransactionProcessingEvent event = record.value();
        if (event == null) {
            log.warn("Null event received on main topic offset={} partition={} — acking (cannot process)", record.offset(), record.partition());
            ack.acknowledge();
            return;
        }
        handle(event, ack);
    }

    @KafkaListener(topics = "${sentinelflow.kafka.topic.retry:" + KafkaTopics.TRANSACTION_PROCESS_RETRY + "}",
            groupId = "${sentinelflow.kafka.consumer.group-id:sentinelflow-risk-workers}",
            containerFactory = "kafkaListenerContainerFactory")
    public void listenRetry(ConsumerRecord<String, TransactionProcessingEvent> record, Acknowledgment ack) {
        TransactionProcessingEvent event = record.value();
        if (event == null) {
            log.warn("Null event received on retry topic offset={} — acking", record.offset());
            ack.acknowledge();
            return;
        }
        handle(event, ack);
    }

    private void handle(TransactionProcessingEvent event, Acknowledgment ack) {
        String eventId = event.eventId();
        String txnRef = event.transactionReference();
        log.info("Consumer received eventId={} transactionReference={} correlationId={}",
                eventId, txnRef, event.correlationId());

        TransactionEventProcessor.ProcessingResult result = processor.process(event);

        switch (result.outcome()) {
            case SUCCEEDED, DUPLICATE -> {
                log.info("Acking eventId={} outcome={}", eventId, result.outcome());
                ack.acknowledge();
            }
            case PERMANENT_FAILURE -> {
                log.warn("Permanent failure eventId={} reason={} -> DLQ {}", eventId, result.reason(), dlqTopic);
                try {
                    kafkaTemplate.send(dlqTopic, txnRef, event).get();
                    ack.acknowledge();
                } catch (Exception e) {
                    log.error("Failed to publish DLQ eventId={} error={}", eventId, e.getMessage());
                    throw new RoutingException("DLQ publish failed: " + result.reason(), e);
                }
            }
            case RETRYABLE_FAILURE -> {
                log.warn("Retryable failure eventId={} attemptReason={} -> retry topic {}", eventId, result.reason(), retryTopic);
                try {
                    kafkaTemplate.send(retryTopic, txnRef, event).get();
                    ack.acknowledge();
                } catch (Exception e) {
                    log.error("Failed to publish retry topic eventId={} error={}", eventId, e.getMessage());
                    throw new RoutingException("Retry publish failed: " + result.reason(), e);
                }
            }
        }
    }

    static class RoutingException extends RuntimeException {
        RoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}