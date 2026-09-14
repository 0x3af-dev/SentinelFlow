# SentinelFlow Kafka — Data Flow

## End-to-End Flow

### 1. Enqueue (producer side)

```
[Internal API] POST /internal/kafka/transactions/{ref}/enqueue
      │  transaction exists?  ── no ──► 404
      ▼
[OutboxService] save TransactionProcessingEvent as PENDING outbox row   (same DB txn as caller)
      │ 202 {eventId, transactionReference, correlationId, outboxId}
      ▼
[OutboxPublisher] @Scheduled(2s) — relays ≤100 PENDING rows:
      KafkaTemplate.send(transactions.process, aggregateId=transactionReference, event)
      │ success ─► status = PUBLISHED
      └─ failure ─► retry_count++; status = FAILED after 5
```

### 2. Consume & process

```
[Consumer] main listener (group sentinelflow-risk-workers, MANUAL_IMMEDIATE ack)
      ▼
[TransactionEventConsumer.listenMain] record → handle(event, ack)
      ▼
[TransactionEventProcessor.process]
   1. event.validate()            ─ invalid ─► saveAttempt PERMANENT_FAILURE → outcome PERMANENT_FAILURE
   2. trackAttempt(eventId)       ─ previous SUCCEEDED ─► outcome DUPLICATE (ack)
                                    ─ first/next ─► attempt PROCESSING (attempt_count+1, upsert, unique event_id)
   3. transaction exists?          ─ no ─► markFailure(retryable=true) → RETRYABLE_FAILURE
   4. pipeline.process(ref) (Phase 2, unchanged)
        ─ success ─► attempt SUCCEEDED → outcome SUCCEEDED (ack)
        ─ PipelineException ─► isRetryable(cause,msg)?
            ├─ retryable ∧ attempts < MAX_RETRIES(5) ─► attempt RETRYABLE_FAILURE → outcome RETRYABLE_FAILURE
            ├─ retryable ∧ attempts ≥ 5 ─────────────► attempt DEAD_LETTERED + lastError → outcome PERMANENT_FAILURE
            └─ not retryable ─────────────────────────► attempt PERMANENT_FAILURE → outcome PERMANENT_FAILURE
```

### 3. Routing (consumer)

```
outcome SUCCEEDED | DUPLICATE          ─► ack.acknowledge()
outcome RETRYABLE_FAILURE              ─► send(retry topic, key=transactionReference) ─► ack
outcome PERMANENT_FAILURE              ─► send(dlq topic,  key=transactionReference) ─► ack
routing send throws                    ─► RoutingException (no ack) → error handler
```

### 4. Retry loop

```
retry topic ─► [Consumer listenRetry] ─► handle(event, ack)  (same path as main)
   └─ same attempts row → attempt_count increments (1,2,…,5) until DEAD_LETTERED → DLQ
```

### 5. Deserialization safety net

```
Malformed bytes on .process or .retry
   ─► ErrorHandlingDeserializer wraps JsonDeserializer
   ─► record value = DeserializationException  (no Kafka-client-level poison)
   ─► container throws ListenerExecutionFailedException
   ─► kafkaErrorHandler (notRetryable: DeserializationException, IllegalArgumentException)
   ─► DeadLetterPublishingRecoverer ▸ dlq topic  (key preserved) ─► offset advances
```

## Topic / Partition Map

| Topic | Producer(s) | Consumer(s) | Key |
|-------|-------------|-------------|-----|
| `.process` | OutboxPublisher, eventPublisher, raw producers | listenMain | transactionReference |
| `.process.retry` | TransactionEventConsumer (RETRYABLE_FAILURE) | listenRetry | transactionReference |
| `.process.dlq` | TransactionEventConsumer, DeadLetterPublishingRecoverer | ops tooling (drain) | transactionReference |

## State Machine (kafka_processing_attempts)

```
            new event                     redelivery                  retryable fail (attempt<N)
                  │                             │                          │
                  ▼                             ▼                          ▼
   (created) ──► PROCESSING ◄────────────────────┘                          │
                  │   ├─ pipeline ok ────────────────► SUCCEEDED ──► (idempotent, DUPLICATE on replay)
                  │   ├─ invalid / non-retryable ─────► PERMANENT_FAILURE ──► DLQ
                  │   └─ retryable (attempt<N) ───────► RETRYABLE_FAILURE ──► retry topic
                  └─ retryable (attempt≥MAX_RETRIES) ─► DEAD_LETTERED ─────► DLQ
```

## Outbox State Machine

```
             enqueue                 relay ok               relay fails ×5
PENDING ─────────────────► PUBLISHED          PENDING ─────────────────► FAILED
        (became PUBLISHED)                    (retry_count 1..5)
```

## Correlation / Observability

- `transactionReference` = partition key and domain correlation ID throughout; `correlationId` spans producer→consumer; both plus `eventId` are set in SLF4J MDC (`MDC.put("eventId"|"correlationId"|"transactionReference")`).
- `com.sentinelflow.kafka` logs at INFO; `org.springframework.kafka` / `org.apache.kafka` at WARN.

## Guarantees Summary

1. At-least-once transport + exactly-once business outcome per `eventId`.
2. Per-transaction ordering within a partition (keyed by transactionReference).
3. Bounded retries (attempts table is authoritative; the retry topic is transport).
4. No poison-pill stalls; malformed input lands in the DLQ.
5. The Phase 2 pipeline and all historical records remain the single source of truth; Kafka never mutates domain data directly.