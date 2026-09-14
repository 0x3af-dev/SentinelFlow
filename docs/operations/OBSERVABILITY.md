# Observability & Reliability Guide

> Phase 7 — SentinelFlow operational intelligence.

## Structured Logging

Every log line uses the structured fields defined in `Md.java`:

| Field | Description | Example |
|---|---|---|
| `operation` | Logical operation identifier | `transaction.pipeline`, `enqueue`, `ai.investigate`, `outbox.publish`, `operations.read` |
| `correlationId` | UUID generated at controller/entry point | `94756046-b085-...` |
| `transactionReference` | Business transaction reference | `TXN-DEMO-001` |
| `investigationId` | Investigation UUID (when in scope) | UUID |
| `status` | `running` / `ok` / `error` | `ok` |
| `durationMs` | Wall-clock time in milliseconds | `577` |
| `errorCode` | Failure code when status is `error` | `ML_UNAVAILABLE` |
| `component` | Java class short name | `TransactionIntelligencePipeline` |
| `eventId` | Optional event identifier | UUID or null |

The format is defined in `src/main/resources/logback-spring.xml` with a `-%d` (ISO-8601) timestamp, thread, component, and the MDC fields above.

## MDC Context Propagation

MDC is set in these locations, each wrapped in `Md.run(...)`:

1. **Transaction pipeline** (`TransactionIntelligencePipeline.process`): `operation=transaction.pipeline`, `correlationId` (UUID), `transactionReference`.
2. **Public enqueue** (`TransactionEventEnqueueController`): `operation=enqueue`, `correlationId` (UUID), `transactionReference`.
3. **Public process** (`TransactionProcessController`): `operation=transaction.pipeline`, `correlationId` (UUID), `transactionReference`.
4. **Internal process** (`TransactionProcessingController`): `operation=transaction.pipeline`, `correlationId`, `transactionReference`.
5. **AI investigation** (`AiInvestigationService.explain`): `operation=ai.investigate`, `correlationId`, `investigationId`.
6. **Outbox relay** (`OutboxPublisher`): `operation=outbox.publish`, `transactionReference`.
7. **Operations API** (`OperationsController`): `operation=operations.read`.

Every log emitted within these scopes automatically carries the MDC fields.

## Correlation ID Lifecycle

```
Controller entry  ──(generates correlationId)──>  pipeline/ML/AI
        │                                              │
        │   (all downstream log lines carry            │
        │    correlationId + transactionReference)      │
        │                                              │
        └────────────── logged at controller level ─────┘
```

The `correlationId` is a UUID generated once at the controller/method entry and flows through:
- Pipeline execution (ML inference, policy evaluation, evidence building)
- Kafka event publishing (outbox relay)
- AI investigation calls
- Error responses

Use it to trace a single request across all log lines: `grep correlationId=<uuid> app.log`.

## Operations API

All endpoints are **read-only** under `/api/operations`:

| Endpoint | Response | Description |
|---|---|---|
| `GET /summary` | `OperationalSummaryResponse` | Probes, outbox/DLQ, attempts, counters, latency |
| `GET /dlq` | `DlqResponse` | Dead-lettered attempts with last error |
| `GET /outbox` | `OutboxResponse` | Pending/failed volumes, oldest pending age |
| `GET /attempts` | `AttemptSummaryResponse` | Status distribution + stuck attempts |
| `GET /integrity` | `IntegrityResponse` | Referential integrity checks |

### Dependency Probes

Each dependency is probed independently with a 5-second TTL cache:

| Probe | Method | Notes |
|---|---|---|
| postgres | `SELECT 1` | Always enabled |
| kafka | `AdminClient.describeCluster()` | Gated on `sentinelflow.kafka.enabled` |
| ml | `GET {ml.service.url}/health` | 2-second WebClient timeout |
| ai | Config + gateway presence | No network call |

Probe results use `DependencyStatus`: `HEALTHY`, `DEGRADED`, `UNAVAILABLE`, `DISABLED`, `UNKNOWN`. Probes never affect Spring Boot liveness/readiness.

### Data Integrity Checks

Six read-only checks verify referential integrity:
- Evidence edges referencing missing nodes
- Risk scores referencing missing feature snapshots
- Triggered rules referencing missing risk scores
- Evidence nodes referencing missing transactions
- Policy simulations referencing missing transactions
- Counterfactuals referencing missing transactions

All checks are `SELECT count(*)` — never mutations.

## Health Actuator

Exposed endpoints (test and production):
- `/actuator/health` — aggregate UP/DOWN
- `/actuator/health/liveness` — liveness probe (process + DB)
- `/actuator/health/readiness` — readiness probe (process + DB only)
- `/actuator/metrics` — Micrometer metrics
- `/actuator/info` — application info

Kafka health is excluded from aggregate readiness (`management.health.kafka.enabled: false`).

## Performance Baseline

Measured on developer laptop (25 pipeline runs, mocked ML):

| Metric | Value |
|---|---|
| p50 | 192 ms |
| p95 | 315 ms |
| min | 115 ms |
| max | 621 ms |

Run with: `mvn -o test -Dtest=PipelineLatencyBaselineTest -Dbenchmark.excluded=`
