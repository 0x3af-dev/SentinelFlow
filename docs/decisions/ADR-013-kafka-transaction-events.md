# ADR-013 — Kafka for Transaction Processing Events

**Status:** Accepted

**Context:** Phase 2 exposed a synchronous pipeline. Phase 3 must process "process this transaction" events asynchronously and reliably, with bounded retries, no duplicates in the historical evidence graph, and dead-letter capture. Kafka was already a stated Phase 3 dependency (see `DOMAIN-BOUNDARIES.md`) and cannot be replaced by a different transport for this scope.

**Decision:** Add a single producer/consumer Kafka flow for transaction processing:

- Three topics, all with 3 partitions, keyed by `transactionReference`: `sentinelflow.transactions.process`, `.retry`, `.dlq`.
- Transport is **at-least-once**; **business processing is exactly-once per `eventId`** using the `kafka_processing_attempts` idempotency row. Explicitly not Kafka EOS/exactly-once.
- Business decisions remain solely in the Phase 2 `TransactionIntelligencePipeline`; consumers route outcomes only.
- Retryable failures go to the retry topic and are bounded by `MAX_RETRIES = 5` recorded in the attempts table; permanent failures and exhausted retries go to the DLQ.
- Malformed payloads are isolated with `ErrorHandlingDeserializer` (no poison-pill partition stall) and recovered to the DLQ by a `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.
- Manual acks (`MANUAL_IMMEDIATE`), `enable.auto.commit=false`, `auto-offset-reset=earliest`.

**Alternatives considered:**
- Spring's non-blocking retry/DLT annotations (`@RetryableTopic`) — rejected: opinionated, less control over the attempts table and test surface.
- Kafka transactions/EOS — rejected: overkill, adds broker config, does not improve the DB-side idempotency guarantee.

**Consequences:** Honest at-least-once guarantee documented and tested; redelivery is idempotent; per-transaction ordering within a partition; a poison event can no longer stall a partition. Adds Kafka infrastructure (docker-compose, Testcontainers) and a config toggle (`sentinelflow.kafka.enabled`).

**References:** ADR-012 (pipeline idempotency), ADR-014 (transactional outbox), `docs/architecture/PHASE-3-KAFKA.md`, `docs/architecture/KAFKA-DATA-FLOW.md`.