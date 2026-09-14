package com.sentinelflow.kafka;

import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.DeviceRepository;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.LocationRepository;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.kafka.attempt.KafkaProcessingAttempt;
import com.sentinelflow.kafka.attempt.KafkaProcessingAttemptRepository;
import com.sentinelflow.kafka.outbox.OutboxEvent;
import com.sentinelflow.kafka.outbox.OutboxEventRepository;
import com.sentinelflow.kafka.outbox.OutboxPublisher;
import com.sentinelflow.kafka.outbox.OutboxService;
import com.sentinelflow.ml.MlInferenceClient;
import com.sentinelflow.ml.MlInferenceRequest;
import com.sentinelflow.risk.FeatureSnapshotRepository;
import com.sentinelflow.risk.RiskScoreRepository;
import com.sentinelflow.shared.dto.MlPrediction;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantRepository;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import com.sentinelflow.transaction.TransactionStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "sentinelflow.kafka.enabled=true",
        "spring.kafka.listener.auto-startup=true"
})
@Testcontainers
@Timeout(120)
class KafkaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired DeviceRepository devices;
    @Autowired LocationRepository locations;
    @Autowired MerchantRepository merchants;
    @Autowired FeatureSnapshotRepository snapshots;
    @Autowired RiskScoreRepository riskScores;
    @Autowired DecisionRecordRepository decisions;
    @Autowired KafkaProcessingAttemptRepository attempts;
    @Autowired OutboxEventRepository outbox;
    @Autowired OutboxService outboxService;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired TransactionEventPublisher eventPublisher;

    @MockitoBean
    MlInferenceClient mlClient;

    private User testUser;
    private Device knownDevice;
    private Location knownLocation;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        testUser = users.save(new User("USR-KAFKA-" + System.nanoTime(), "Kafka User", null, UserStatus.ACTIVE));
        knownDevice = devices.save(new Device(testUser, "DEV-KAFKA-" + System.nanoTime(), "MOBILE", "ANDROID", Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));
        knownLocation = locations.save(new Location(testUser, "IN", "Karnataka", "Bengaluru", 12.97, 77.59, Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));
        merchant = merchants.save(new Merchant("MRC-KAFKA-" + System.nanoTime(), "KafkaMart", "GROCERY", "IN", MerchantStatus.ACTIVE));

        when(mlClient.infer(any(MlInferenceRequest.class))).thenAnswer(inv -> lowRiskPrediction());
    }

    private MlPrediction lowRiskPrediction() {
        return new MlPrediction("risk-model", "v1", "fs-v1", 0.2, "LOW", List.of(), Map.of("model_type", "mock"), 4, Instant.now());
    }

    private Transaction createTxn(String ref, BigDecimal amount) {
        return transactions.save(new Transaction(ref + "-" + System.nanoTime(), testUser, merchant, knownDevice, knownLocation,
                amount, "INR", "PURCHASE", "ONLINE", Instant.parse("2026-09-01T10:00:00Z"), TransactionStatus.RECEIVED));
    }

    private void await(Supplier<Boolean> condition) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Boolean.TRUE.equals(condition.get())) return;
            } catch (Exception ignored) {
                // retry
            }
            sleepQuietly(250);
        }
        throw new AssertionError("Condition not met within 30s");
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private KafkaProcessingAttempt attemptFor(String eventId) {
        return attempts.findByEventId(eventId)
                .orElseThrow(() -> new AssertionError("no attempt row for " + eventId));
    }

    private List<ConsumerRecord<String, String>> drainTopic(String topic, int expected) {
        return drainTopic(topic, expected, key -> true);
    }

    private List<ConsumerRecord<String, String>> drainTopic(String topic, int expected, Predicate<String> keyFilter) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-drain-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"
        );
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 30_000;
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(topic));
            while (System.currentTimeMillis() < deadline && found.size() < Math.max(expected, 1)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(record -> {
                    if (keyFilter.test(record.key())) {
                        found.add(record);
                    }
                });
            }
        }
        assertThat(found).isNotEmpty();
        assertThat(found.stream().map(ConsumerRecord::key)).allMatch(keyFilter);
        return found;
    }

    private void publishRaw(String topic, String key, String value) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.ACKS_CONFIG, "all"
        );
        try (var producer = new KafkaProducer<String, String>(props)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void publishEvent(TransactionProcessingEvent event) {
        try {
            eventPublisher.publish(event).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------

    @Test
    void eventFlowProducesDecisionAndSucceeds() {
        Transaction txn = createTxn("TXN-KAFKA-OK", new BigDecimal("500.00"));
        TransactionProcessingEvent event = TransactionProcessingEvent.create(txn.getTransactionReference(), "corr-ok-1");
        publishEvent(event);

        await(() -> {
            var a = attempts.findByEventId(event.eventId());
            return a.isPresent() && a.get().getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED;
        });

        KafkaProcessingAttempt attempt = attemptFor(event.eventId());
        assertThat(attempt.getTransactionReference()).isEqualTo(txn.getTransactionReference());
        assertThat(attempt.getCorrelationId()).isEqualTo("corr-ok-1");
        assertThat(attempt.getEventType()).isEqualTo(TransactionProcessingEvent.TYPE_TRANSACTION_PROCESS);
        assertThat(attempt.getEventVersion()).isEqualTo(1);
        assertThat(attempt.getAttemptCount()).isEqualTo(1);
        assertThat(attempt.getLastError()).isNull();

        assertThat(snapshots.findByTransactionId(txn.getId())).hasSize(1);
        assertThat(riskScores.findByTransactionId(txn.getId())).hasSize(1);
        assertThat(decisions.findByTransactionId(txn.getId())).hasSize(1);
        assertThat(transactions.findByTransactionReference(txn.getTransactionReference()).get().getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void duplicateEventIsIdempotent() {
        Transaction txn = createTxn("TXN-KAFKA-DUP", new BigDecimal("500.00"));
        TransactionProcessingEvent event = TransactionProcessingEvent.create(txn.getTransactionReference(), "corr-dup-1");
        publishEvent(event);
        await(() -> attempts.findByEventId(event.eventId()).map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED).orElse(false));

        publishEvent(event);

        await(() -> {
            var a = attempts.findByEventId(event.eventId());
            return a.map(x -> x.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED && x.getAttemptCount() == 1).orElse(false);
        });

        assertThat(attempts.findByEventId(event.eventId())).isPresent();
        assertThat(decisions.findByTransactionId(txn.getId())).hasSize(1);
    }

    @Test
    void fiveDuplicateDeliveriesStillSingleDecision() {
        Transaction txn = createTxn("TXN-KAFKA-5DUP", new BigDecimal("500.00"));
        TransactionProcessingEvent event = TransactionProcessingEvent.create(txn.getTransactionReference(), "corr-5dup-1");
        for (int i = 0; i < 5; i++) {
            publishEvent(event);
        }
        await(() -> attempts.findByEventId(event.eventId())
                .map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED && a.getAttemptCount() == 1)
                .orElse(false));
        await(() -> decisions.findByTransactionId(txn.getId()).size() == 1);
        assertThat(riskScores.findByTransactionId(txn.getId())).hasSize(1);
    }

    @Test
    void structurallyInvalidEventRoutedToDlq() {
        Transaction txn = createTxn("TXN-KAFKA-INVALID", new BigDecimal("500.00"));
        // Well-formed JSON that deserializes but fails semantic validation (unknown eventType)
        String json = """
                {"eventId":"inv-1","eventType":"UNKNOWN_TYPE","eventVersion":1,"transactionReference":"%s",
                 "occurredAt":"2026-09-01T10:00:00Z","correlationId":"corr-invalid-1","producer":"bad-producer","schemaVersion":"v1"}
                """.formatted(txn.getTransactionReference());
        publishRaw(KafkaTopics.TRANSACTION_PROCESS, txn.getTransactionReference(), json);

        await(() -> attempts.findByEventId("inv-1")
                .map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.PERMANENT_FAILURE)
                .orElse(false));
        drainTopic(KafkaTopics.TRANSACTION_PROCESS_DLQ, 1, k -> txn.getTransactionReference().equals(k));
        assertThat(attemptFor("inv-1").getStatus()).isEqualTo(KafkaProcessingAttempt.AttemptStatus.PERMANENT_FAILURE);
    }

    @Test
    void malformedJsonRoutedToDlqViaDeserializer() {
        long attemptsBefore = attempts.count();
        publishRaw(KafkaTopics.TRANSACTION_PROCESS, "txn-raw-1", "{ this is not valid json!! ");
        drainTopic(KafkaTopics.TRANSACTION_PROCESS_DLQ, 1, "txn-raw-1"::equals);
        // no attempt row should ever be created for the malformed payload
        assertThat(attempts.count()).isEqualTo(attemptsBefore);
    }

    @Test
    void unknownTransactionRetriedThenDeadLettered() {
        TransactionProcessingEvent event = TransactionProcessingEvent.create("TXN-NEVER-EXISTS-" + System.nanoTime(), "corr-unknown-1");
        publishEvent(event);

        await(() -> attempts.findByEventId(event.eventId())
                .map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.DEAD_LETTERED)
                .orElse(false));

        KafkaProcessingAttempt attempt = attemptFor(event.eventId());
        assertThat(attempt.getStatus()).isEqualTo(KafkaProcessingAttempt.AttemptStatus.DEAD_LETTERED);
        assertThat(attempt.getAttemptCount()).isEqualTo(TransactionEventProcessor.MAX_RETRIES);
        assertThat(attempt.getLastError()).contains("Transaction not found");
        drainTopic(KafkaTopics.TRANSACTION_PROCESS_DLQ, 1, k -> event.transactionReference().equals(k));
    }

    @Test
    void transientMlFailureRecoversAfterRetries() {
        Transaction txn = createTxn("TXN-KAFKA-MLRETRY", new BigDecimal("500.00"));
        TransactionProcessingEvent event = TransactionProcessingEvent.create(txn.getTransactionReference(), "corr-mlretry-1");

        when(mlClient.infer(any(MlInferenceRequest.class)))
                .thenThrow(new MlInferenceClient.MlInferenceException("ML inference timeout"))
                .thenThrow(new MlInferenceClient.MlInferenceException("ML inference timeout"))
                .thenAnswer(inv -> lowRiskPrediction());

        publishEvent(event);

        await(() -> attempts.findByEventId(event.eventId())
                .map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED)
                .orElse(false));

        KafkaProcessingAttempt attempt = attemptFor(event.eventId());
        assertThat(attempt.getAttemptCount()).isGreaterThanOrEqualTo(3);
        assertThat(decisions.findByTransactionId(txn.getId())).hasSize(1);
        assertThat(transactions.findById(txn.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void concurrentDifferentEventsAllSucceed() throws Exception {
        int total = 6;
        List<TransactionProcessingEvent> events = new ArrayList<>();
        java.util.concurrent.ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Transaction> txns = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            Transaction txn = createTxn("TXN-KAFKA-CONC-" + i, new BigDecimal("500.00"));
            txns.add(txn);
            events.add(TransactionProcessingEvent.create(txn.getTransactionReference(), "corr-conc-" + i));
        }
        var futures = events.stream()
                .map(e -> pool.submit(() -> {
                    try {
                        eventPublisher.publish(e).get();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                }))
                .toList();
        for (var f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        for (TransactionProcessingEvent e : events) {
            await(() -> attempts.findByEventId(e.eventId())
                    .map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED)
                    .orElse(false));
            assertThat(attempts.findByEventId(e.eventId()).orElseThrow().getAttemptCount()).isEqualTo(1);
        }
        for (Transaction txn : txns) {
            assertThat(decisions.findByTransactionId(txn.getId())).hasSize(1);
        }
    }

    @Test
    void twoDistinctEventsCreateHistoricalLineage() {
        Transaction txn = createTxn("TXN-KAFKA-HIST", new BigDecimal("500.00"));

        TransactionProcessingEvent e1 = TransactionProcessingEvent.create(txn.getTransactionReference(), "corr-hist-1");
        publishEvent(e1);
        await(() -> attempts.findByEventId(e1.eventId())
                .map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED).orElse(false));

        TransactionProcessingEvent e2 = TransactionProcessingEvent.create(txn.getTransactionReference(), "corr-hist-2");
        publishEvent(e2);
        await(() -> attempts.findByEventId(e2.eventId())
                .map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED).orElse(false));

        // immutable history: two snapshots/risk scores/decisions, both attempts succeeded
        assertThat(snapshots.findByTransactionId(txn.getId())).hasSize(2);
        assertThat(riskScores.findByTransactionId(txn.getId())).hasSize(2);
        assertThat(decisions.findByTransactionId(txn.getId())).hasSize(2);
        assertThat(attemptFor(e1.eventId()).getStatus()).isEqualTo(KafkaProcessingAttempt.AttemptStatus.SUCCEEDED);
        assertThat(attemptFor(e2.eventId()).getStatus()).isEqualTo(KafkaProcessingAttempt.AttemptStatus.SUCCEEDED);
    }

    @Test
    void outboxRelayPublishesAndProcesses() {
        Transaction txn = createTxn("TXN-KAFKA-OUTBOX", new BigDecimal("500.00"));
        TransactionProcessingEvent event = TransactionProcessingEvent.create(txn.getTransactionReference(), "corr-outbox-1");

        OutboxEvent row = outboxService.saveTransactionEvent(event);
        assertThat(row.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);

        outboxPublisher.publishPending();

        await(() -> outbox.findById(row.getId())
                .map(o -> o.getStatus() == OutboxEvent.OutboxStatus.PUBLISHED).orElse(false));
        await(() -> attempts.findByEventId(event.eventId())
                .map(a -> a.getStatus() == KafkaProcessingAttempt.AttemptStatus.SUCCEEDED).orElse(false));

        assertThat(decisions.findByTransactionId(txn.getId())).hasSize(1);
        assertThat(outbox.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
    }
}