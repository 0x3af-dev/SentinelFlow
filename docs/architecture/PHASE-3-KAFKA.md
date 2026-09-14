# SentinelFlow Phase 3 — Reliable Asynchronous Event Processing

## Overview

Phase 3 adds reliable, asynchronous processing of "process this transaction" events over Kafka. The Phase 2 `TransactionIntelligencePipeline` remains the **single source of truth** for risk decisioning — Kafka is transport only and contains no business logic.

```
Enqueue (internal API / outbox) → Kafka sentinelflow.transactions.process
  → sentinelflow-risk-workers consumer → TransactionEventProcessor
    → TransactionIntelligencePipeline (Phase 2, unchanged)
  → SUCCEEDED | DUPLICATE            → ack
  → RETRYABLE_FAILURE (bounded)      → sentinelflow.transactions.process.retry
  → PERMANENT_FAILURE / DEAD_LETTERED → sentinelflow.transactions.process.dlq
```

## Delivery Guarantee

- **Kafka transport is at-least-once.** Consumers use manual acks and `auto-offset-reset: earliest`; a message acknowledged before its side effects are committed may be redelivered.
- **Business processing is exactly-once per eventId** via the `kafka_processing_attempts` idempotency row (unique `event_id`, status checks). Redeliveries are detected and acked as `DUPLICATE`.
- This is **not** Kafka exactly-once processing (no EOS/transactions). The guarantee is honest and test-backed.

## Kafka Infrastructure

- **Broker:** KRaft-mode Kafka (`apache/kafka:3.9.0`) via docker-compose; topics are **pre-created by `KafkaAdmin`** at startup (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`).
- **Topics** (`KafkaTopics`):

| Topic | Partitions | Purpose |
|-------|-----------|---------|
| `sentinelflow.transactions.process` | 3 | Main ingestion |
| `sentinelflow.transactions.process.retry` | 3 | Bounded retry loop |
| `sentinelflow.transactions.process.dlq` | 3 | Permanent failures / dead letters |

- **Partition key:** `transactionReference` → per-transaction ordering within a partition.
- **Producer:** acks=all, idempotence enabled, key/value serializer = String/JsonSerializer.
- **Consumer:** group `sentinelflow-risk-workers` (override `KAFKA_CONSUMER_GROUP`), concurrency 3, MANUAL_IMMEDIATE acks, `ErrorHandlingDeserializer` wrapping the typed `JsonDeserializer` so malformed payloads flow to the DLQ instead of poisoning a partition.

## Components

### Producer / Outbox
- `TransactionEventEnqueueController` — `POST /internal/kafka/transactions/{transactionReference}/enqueue` → `202` with `{eventId, transactionReference, correlationId, outboxId}`; `404` if the transaction does not exist. Writes a `PENDING` outbox row.
- `OutboxService` — persists a `PENDING` outbox row for an event (transactional outbox; can be extended to write in the same DB transaction as the source change).
- `OutboxPublisher` — `@Scheduled(fixedDelay=2000)` relay: publishes up to 100 `PENDING` outbox rows to Kafka, marks them `PUBLISHED`; on failure increments `retry_count`, and marks `FAILED` after 5 attempts.
- `KafkaProducerConfig` — typed `KafkaTemplate<String, TransactionProcessingEvent>` bean, `dlqRecoveryTemplate` (`KafkaTemplate<String,Object>`) for the error-handler recoverer, `kafkaBootstrapServers` (prefers Testcontainers/`KafkaConnectionDetails`, falls back to `spring.kafka.bootstrap-servers`), and the three `NewTopic`s.

### Consumer / Processor
- `TransactionEventConsumer` — thin `@KafkaListener` on the main and retry topics. Routes outcomes: `SUCCEEDED`/`DUPLICATE` → ack; `RETRYABLE_FAILURE` → retry topic; `PERMANENT_FAILURE` → DLQ. If the routing publish fails it throws `RoutingException` so the record is not acked and will be retried (or recovered) by the error handler.
- `TransactionEventProcessor` — coordinator, **not `@Transactional`** (preserves the Phase 2 invariant: no DB transaction held across the ML HTTP call; each status update is its own short transaction).
  - `event.validate()` (semantic validation) → `IllegalArgumentException` ⇒ `PERMANENT_FAILURE`.
  - Idempotency via `kafka_processing_attempts`; a prior `SUCCEEDED` ⇒ `DUPLICATE`.
  - Unknown transaction ⇒ `RETRYABLE_FAILURE`.
  - Pipeline failure ⇒ classified via `isRetryable` (DB/connection/timeout/503 → retryable; validation/malformed → not).
  - `MAX_RETRIES = 5`; exhausted retries marked `DEAD_LETTERED` and routed to DLQ.

### Error Handling
- `KafkaErrorConfig.kafkaErrorHandler` — `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` (▸ `sentinelflow.transactions.process.dlq`), `FixedBackOff(1000ms, 2)`, and `addNotRetryableExceptions(IllegalArgumentException, DeserializationException)`.
- `KafkaConsumerConfig.kafkaListenerContainerFactory` wires that handler into every listener container (`setCommonErrorHandler`).
- `ErrorHandlingDeserializer` converts Kafka-client-level serialization failures into records the listener error handler can recover, so a malformed payload is **dead-lettered and the partition offset advances** (no poison-pill stall).

## Database

- `kafka_processing_attempts` — per-event processing state (see `KafkaProcessingAttempt.AttemptStatus`). Attempt statuses: `RECEIVED`, `PROCESSING`, `SUCCEEDED`, `RETRYABLE_FAILURE`, `PERMANENT_FAILURE`, `DEAD_LETTERED`. Unique on `event_id`; indexed on `transaction_reference`, `status`, `correlation_id`.
- `outbox_events` — transactional outbox rows: `PENDING` / `PUBLISHED` / `FAILED`, `retry_count`, JSONB `payload`.

## Event Contract (v1)

`TransactionProcessingEvent` (JSON, no type headers; `TRUSTED_PACKAGES=com.sentinelflow.kafka`):

| Field | Type | Notes |
|-------|------|-------|
| `eventId` | string | unique, idempotency key |
| `eventType` | string | `TRANSACTION_PROCESS` |
| `eventVersion` | int | `1` |
| `transactionReference` | string | also the partition key |
| `occurredAt` | Instant (ISO-8601) | |
| `correlationId` | string | end-to-end tracing |
| `producer` | string | |
| `schemaVersion` | string | `v1` |

`event.validate()` rejects missing/empty mandatory fields and unknown `eventType`.

## Reliability Story

- Transactional outbox prevents dual-write loss when enqueue source mutations and event publication share a DB transaction.
- Idempotent processing makes redelivery harmless and keeps the historical evidence graph single-writer.
- Bounded retry (attempts table + retry topic) separates transient failures from poison events.
- DLQ capture plus a dead-letter record is the recovery path for anything the consumer cannot process.
- `KafkaIntegrationTest` covers: happy path, duplicate/multi-delivery idempotency, malformed JSON → DLQ (no poison), invalid-but-parseable JSON → DLQ, unknown transaction → retry dead-letter, transient ML failure → recovery, concurrent distinct events, two events ⇒ historical lineage, and the outbox relay.

## Configuration (`application.yml`)

```yaml
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
sentinelflow.kafka.enabled: ${KAFKA_ENABLED:true}     # master toggle (@ConditionalOnProperty)
sentinelflow.kafka.topic.transactions: ...process
sentinelflow.kafka.topic.retry:        ...process.retry
sentinelflow.kafka.topic.dlq:          ...process.dlq
sentinelflow.kafka.topic.partitions:   3
sentinelflow.kafka.consumer.group-id:  sentinelflow-risk-workers
```

Phase 1/2 tests run with `sentinelflow.kafka.enabled=false` and `spring.kafka.listener.auto-startup=false` (see `src/test/resources/application.yml`); Kafka integration tests override both to `true`.

## Failure Matrix

| Failure | Classification | Routing |
|---------|----------------|---------|
| Malformed JSON / deserialization error | not retryable | DLQ (error handler) |
| Invalid event (fails `event.validate()`) | PERMANENT_FAILURE | DLQ (consumer) |
| Unknown transaction | RETRYABLE_FAILURE | retry topic → DLQ after 5 |
| ML timeout/connection/unavailable | retryable | retry topic → DLQ after 5 |
| DB (`DataAccessException`) during processing | retryable | retry topic → DLQ after 5 |
| Kafka routing publish failure | not acked | error handler → DLQ |

## Testing

`mvn test -Dtest=KafkaIntegrationTest` — Testcontainers PostgreSQL 16 + Kafka (cp-kafka) with `@ServiceConnection`; ML client mocked via `@MockitoBean`. Full suite: `mvn test` (67 tests).