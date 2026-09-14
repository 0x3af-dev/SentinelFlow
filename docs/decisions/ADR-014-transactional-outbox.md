# ADR-014 — Transactional Outbox for Reliable Publication

**Status:** Accepted

**Context:** When a business change and its Kafka event must both persist, a direct DB-write-then-Kafka-publish sequence creates a dual-write problem: the DB commit may succeed while the publish fails (event lost), or the publish may succeed while the commit rolls back (phantom event). Phase 3 needs reliable event publication.

**Decision:** Use a transactional outbox table (`outbox_events`):

- Event publication intent is recorded as a `PENDING` row in the **same database transaction** as the source change (`OutboxService`); the source of truth for publication is the DB row, not a runtime publish call.
- A scheduled relay (`OutboxPublisher`, `@Scheduled(fixedDelay=2000)`) publishes `PENDING` rows (batch ≤100, earliest first) to Kafka and marks them `PUBLISHED` in the same transaction as the publish attempt.
- Publish failures increment `retry_count`; after 5 failed attempts the row is marked `FAILED` for operator review — nothing is silently dropped.
- Consumers guarantee idempotency (see ADR-013), so republishing an already-delivered event after a crash of the relay is harmless.

**Alternatives considered:**
- Direct publish in the service/controller (no outbox) — rejected: dual-write loss window.
- Outbox via CDC (Debezium) — rejected: extra infrastructure and moving-parts not justified at this scale.
- Kafka transactions to bridge DB/Kafka — rejected: matches ADR-013 reasoning; does not remove the idempotency requirement.

**Consequences:** Reliable publication with a recoverable audit trail, no new infrastructure beyond one table; adds a 2s-max relay latency. The current enqueue endpoint persists the outbox row in the same transaction as its read; any future source mutation should do the same (single transaction), at which point the dual-write window is closed entirely.

**References:** ADR-013, `docs/architecture/KAFKA-DATA-FLOW.md` (outbox state machine).