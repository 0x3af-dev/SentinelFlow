# ADR-020: Phase 7 Observability, Resilience & Operational Intelligence

- **Status:** accepted (Phase 7)
- **Date:** 2026-09-14
- **Deciders:** backend, operations, security
- **Technical story:** Phase 7 must add production-grade observability, structured
  logging, dependency health probing, data integrity verification, and
  operational intelligence endpoints without weakening the existing 149-test
  safety net or altering Phase 2/3 decision semantics.

## Context

SentinelFlow has a working transaction pipeline (Phase 2), Kafka event relay
(Phase 3), full decision replay (Phase 4), and AI investigation (Phase 6).
However:

1. **Logging is unstructured.** Without consistent fields (`correlationId`,
   `transactionReference`, `operation`), tracing a single request across
   pipeline stages, ML inference, Kafka publishing, and AI investigation
   requires grepping for timestamps — impractical in production.
2. **Dependencies are opaque.** A Postgres outage, Kafka broker failure, or ML
   service crash produces generic error logs. There is no structured health
   endpoint or dependency probe mechanism.
3. **No operational dashboard.** Ops teams have no way to see outbox backlog,
   DLQ depth, attempt distribution, or counter metrics without direct database
   queries.
4. **No data integrity verification.** Referential integrity relies on FK
   constraints, but there is no runtime check to verify the constraints hold.

## Decision

### 1. Structured MDC logging

Every request-scoped operation sets MDC fields via `Md.of(operation,
correlationId, transactionReference, investigationId)` wrapped in
`Md.run(fields, Supplier<T>)`. This produces consistent structured log lines
with:
- `operation` (pipeline, enqueue, ai, outbox, operations)
- `correlationId` (UUID generated at entry)
- `transactionReference`
- `investigationId` (when in scope)
- `status`, `durationMs`, `errorCode`

The logback pattern (`logback-spring.xml`) outputs these fields on every line.
No log line is unstructured.

### 2. Read-only Operations API

`OperationsController` exposes five endpoints under `/api/operations`:

- `GET /summary` — dependency probes, outbox/DLQ counts, attempt distribution,
  counters, latency means
- `GET /dlq` — dead-lettered attempts with last error
- `GET /outbox` — pending/failed volumes, oldest pending age
- `GET /attempts` — status distribution + stuck attempts
- `GET /integrity` — six referential integrity checks

All endpoints are **read-only**. They never mutate state, never replay events,
never re-drive the outbox, and never delete data.

### 3. Dependency probes with TTL cache

`OperationalProbeService` probes each dependency independently:
- Postgres: `SELECT 1`
- Kafka: `AdminClient.describeCluster()` (gated on `sentinelflow.kafka.enabled`)
- ML: `GET {ml.service.url}/health` with 2-second WebClient timeout
- AI: Configuration + gateway presence check (no network call)

Results are cached for 5 seconds (`ConcurrentHashMap` with TTL). Probes use
`DependencyStatus` vocabulary: HEALTHY, DEGRADED, UNAVAILABLE, DISABLED,
UNKNOWN. **Probes never affect Spring Boot liveness/readiness.** Actuator
readiness is limited to process + DB only.

### 4. Data integrity checks

`DataIntegrityService` runs six `SELECT count(*)` queries to detect orphaned
rows in evidence edges, risk scores, triggered rules, evidence nodes, policy
simulations, and counterfactuals. All checks are read-only diagnostics — never
mutations.

### 5. Metrics instrumentation

`SentinelFlowMetrics` tracks counters for:
- Transaction processing (processed, succeeded, failed)
- Decision distribution by type (ALLOW, REVIEW, BLOCK)
- ML requests, successes, failures
- AI requests, successes, failures (with error code tags)
- Kafka consumed, succeeded, duplicate, retryable, permanent, dead-lettered

Counters are plain `Counter` beans (no tagged `increment(double, String...)`
API since the deployed Micrometer version does not support it). Each counter is
registered individually and summed across meters.

### 6. Failure mapping consistency

Every dependency failure maps to a coherent HTTP response:
- Database: 503 `DEPENDENCY_UNAVAILABLE`
- ML: 503 `ML_UNAVAILABLE`
- AI disabled: 503 `AI_UNAVAILABLE` (fast-path, no persistence)
- AI provider failure: 503 `AI_UNAVAILABLE` (with audit run)
- AI invalid response: 502 `AI_RESPONSE_INVALID`
- Pipeline failure: 500 `INTERNAL_ERROR`

### 7. Benchmark baseline

`PipelineLatencyBaselineTest` (@Tag("benchmark"), excluded from default suite)
measures p50/p95 latency over 25 pipeline runs. Real measured numbers on
developer laptop:

| Metric | Value |
|---|---|
| p50 | 192 ms |
| p95 | 315 ms |
| min | 115 ms |
| max | 621 ms |

Numbers are baseline, not performance gates.

## Alternatives Considered

1. **External observability platform (Datadog, Grafana Cloud).** Provides
   dashboards, alerting, and distributed tracing out of the box. Rejected for
   Phase 7 because the platform is still in development mode; adding a SaaS
   dependency introduces cost, vendor lock-in, and secrets management overhead
   that is premature.

2. **OpenTelemetry auto-instrumentation.** Would give distributed tracing
   without manual MDC. Rejected because: (a) the current Spring Boot version
   requires significant configuration for OTel, (b) the project uses WebFlux
   with Reactor which complicates context propagation, (c) MDC gives us the
   same correlation without the OTel dependency.

3. **No structured logging.** Continue with free-text logs. Rejected because
   production debugging requires consistent fields for grep/jq/ELK ingestion.

## Consequences

- **Positive:** Every request can be traced across pipeline, ML, AI, and Kafka
  via `correlationId`. Ops teams get a single API for health, backlog, and
  metrics without database queries.
- **Positive:** Dependency failures produce clear HTTP 503 with controlled
  error codes instead of opaque 500s. The frontend can display specific
  degradation messages.
- **Positive:** Data integrity checks provide runtime FK verification that
  catches application-level writes bypassing aggregates.
- **Positive:** The benchmark baseline establishes a measurable performance
  floor for future regression detection.
- **Negative:** MDC wrapping adds ~1 microsecond per request (negligible).
- **Negative:** Probe TTL cache means dependency status can be stale for up to
  5 seconds (acceptable for operational dashboard use).
- **Risk:** Metrics counters use in-memory Micrometer; restarting the
  application resets all counters. This is acceptable for Phase 7 since the
  platform is pre-production.
