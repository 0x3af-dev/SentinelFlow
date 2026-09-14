package com.sentinelflow.kafka;

import com.sentinelflow.metrics.SentinelFlowMetrics;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "sentinelflow.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaErrorConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(@Qualifier("dlqRecoveryTemplate") KafkaTemplate<String, Object> recoveryTemplate,
                                                 SentinelFlowMetrics metrics) {
        // Safety net for events the consumer could not route or deserialize.
        // DLQ routing happens here; normal retry routing happens in the consumer.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(recoveryTemplate,
                (record, ex) -> {
                    String dlq = KafkaTopics.TRANSACTION_PROCESS_DLQ;
                    log.warn("Recovering record topic={} offset={} partition={} to DLQ {} error={}",
                            record.topic(), record.offset(), record.partition(), dlq, ex.getMessage());
                    metrics.kafkaDeadLettered();
                    return new TopicPartition(dlq, record.partition());
                });

        // Keep the container alive while the underlying issue resolves.
        FixedBackOff backOff = new FixedBackOff(1000L, 2L);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Deserialization / schema errors never resolve by retrying in place.
        handler.addNotRetryableExceptions(IllegalArgumentException.class, DeserializationException.class);

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Kafka container retry attempt={} for record topic={} offset={} error={}",
                        deliveryAttempt, record.topic(), record.offset(), ex.getMessage())
        );

        return handler;
    }
}
