package com.sentinelflow.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "sentinelflow.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaProducerConfig {

    /**
     * Effective bootstrap servers: Testcontainers-provided connection details when
     * present (tests), otherwise the configured property (default localhost:9092).
     */
    @Bean
    @Qualifier("kafkaBootstrapServers")
    String kafkaBootstrapServers(ObjectProvider<KafkaConnectionDetails> connections,
                                 @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String configured) {
        KafkaConnectionDetails details = connections.getIfAvailable();
        if (details != null && !details.getBootstrapServers().isEmpty()) {
            return String.join(",", details.getBootstrapServers());
        }
        return configured;
    }

    @Bean
    public NewTopic transactionProcessTopic(
            @Value("${sentinelflow.kafka.topic.transactions:" + KafkaTopics.TRANSACTION_PROCESS + "}") String name,
            @Value("${sentinelflow.kafka.topic.partitions:3}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionProcessDlqTopic(
            @Value("${sentinelflow.kafka.topic.dlq:" + KafkaTopics.TRANSACTION_PROCESS_DLQ + "}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionProcessRetryTopic(
            @Value("${sentinelflow.kafka.topic.retry:" + KafkaTopics.TRANSACTION_PROCESS_RETRY + "}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public KafkaTemplate<String, TransactionProcessingEvent> kafkaTemplate(
            @Value("#{kafkaBootstrapServers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        DefaultKafkaProducerFactory<String, TransactionProcessingEvent> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }

    /**
     * Object-typed producer used by the container error handler to publish DLQ
     * records — including records whose payload failed to deserialize (raw bytes
     * are serialized defensively).
     */
    @Bean
    public KafkaTemplate<String, Object> dlqRecoveryTemplate(
            @Value("#{kafkaBootstrapServers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }
}
