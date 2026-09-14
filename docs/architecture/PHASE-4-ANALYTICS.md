# Phase 4 — Decision Analytics (Simulation, Policy Lab, Counterfactual, Investigation Foundation)

**Scope:** backend analytics modules built on top of the Phase 1–3 pipeline. Everything here is **read-only / analysis-only** with respect to production records.

**Non-negotiable invariants (enforced by design and by tests):**
- No Phase 4 code mutates `Transaction`, `FeatureSnapshot`, `RiskScore`, `RiskFactor`, `DecisionRecord`, `DecisionPolicy`, `ModelVersion`, `EvidenceNode`, or `EvidenceEdge`.
- No Phase 4 code writes to Kafka.
- No Phase 4 code invokes the rule engine or the production `PolicyEvaluator`. Simulation semantics are replicated read-only in `ThresholdDecision` and verified against the spec's acceptance cases (see `DECISION-SIMULATION.md`).
- There is **no causal claim** anywhere: counterfactuals are labelled *hypothetical* and every response carries a disclaimer.
- The production decision for any transaction is never rewritten by any analytical action.

---

## Modules (package `com.sentinelflow.analytics`)

| Module | Package | Responsibility |
|--------|---------|----------------|
| Decision Replay | `analytics.replay` | Read-only reconstruction of a transaction's scoring & decision lineage from persisted records |
| Policy Lab | `analytics.policy` | What-if policy threshold changes on a real, already-decided transaction (single + batch) |
| Counterfactual | `analytics.counterfactual` | What-if feature changes on a real transaction, re-scored through the ML client (hypothetical only) |
| Disagreement Analysis | `analytics.dto` (`DisagreementInfo.of`) | Pure, tested classification of model-vs-rule disagreement for a transaction (included in the replay response) |
| Evidence Graph | `analytics.shared` (`EvidenceGraphCollector`) | Bounded read of the persisted evidence graph for a transaction |
| DTOs | `analytics.dto` | API payloads (records) — the only objects crossing the HTTP boundary |
| Exceptions | `analytics.exception` | Structured, errror-code-bearing exceptions (`AnalyticsValidationException`, `AnalyticsNotFoundException`, `AnalyticsMlUnavailableException`) |

Plus the investigation application layer in `com.sentinelflow.investigation.service` (`InvestigationApplicationService`, `InvestigationEventPublisher`), which composes the analytical modules into a read-only investigation workflow.

---

## Persistence (V10, append-only)

Two new tables (`V10__analytics_simulation_tables.sql`):

### `policy_simulations`
Append-only record of every policy-lab run. Rows are never updated after insertion.
- `transaction_id` → `transactions.id` (FK)
- `decision_record_id` → `decision_records.id` (FK) — the production decision the simulation is compared against
- `feature_snapshot_id` → `feature_snapshots.id` (FK) — the immutable feature vector used
- `policy_name` (default `fraud-policy`), `simulation_label`
- `review_threshold`, `block_threshold`
- `actual_decision`, `simulated_decision`, `decision_changed`, `change_type` (`UNCHANGED` / `MORE_PERMISSIVE` / `MORE_RESTRICTIVE`)
- `investigation_id` (nullable) → `investigations.id` — set when a simulation is recorded onto an existing investigation
- `performed_by`, `created_at`, `updated_at`
- CHECK: simulated thresholds must be `0 < review <= block < 1`

### `counterfactual_analyses`
Append-only record of every counterfactual run.
- `transaction_id` → `transactions.id` (FK)
- `risk_score_id` → `risk_scores.id` (FK) — the production score used as the baseline
- `feature_snapshot_id` → `feature_snapshots.id` (FK) — baseline feature vector
- `modifications` (JSONB) — the exact validated modifications applied (`[{feature, original, modified, step}]`)
- `original_risk_score`, `hypothetical_risk_score`, `original_decision`, `hypothetical_decision`, `decision_changed`, `change_type`
- `model_prediction_time_ms`
- `investigation_id` (nullable), `performed_by`, `created_at`, `updated_at`

Both tables follow the existing schema conventions: `UUID` PK, `created_at`/`updated_at` `TIMESTAMPTZ NOT NULL DEFAULT NOW()`, FKs to the reference tables. `ddl-auto: validate` is satisfied by including `updated_at` (entities extend `BaseEntity`).

Reasons for persisting instead of computing ephemeral results: reproducibility and auditability are core spec themes; every simulation / counterfactual is a reviewable scientific artifact tied to an immutable decision and feature snapshot.

---

## API Surface

Controllers are in `com.sentinelflow.api` (the app is WebFlux-only; annotated `@RestController` returning `ResponseEntity` works).

| Endpoint | Status | Purpose |
|----------|--------|---------|
| `GET /api/transactions/{ref}/decision-replay` | 200 | Full lineage: transaction, feature snapshot, model, risk score, risk factors, triggered rules, policy, decision, evidence, disagreement |
| `POST /api/policy-lab/simulate` | 200 | Single what-if policy simulation |
| `POST /api/policy-lab/simulate-batch` | 200 | Batch simulation (list of transaction refs) — per-transaction result list, each persisted separately |
| `GET /api/policy-lab/transactions/{ref}/simulations` | 200 | Historical simulations for a transaction |
| `POST /api/counterfactuals` | 201 | What-if feature modification (removed in Phase 4 per roadmap) |
| `GET /api/counterfactuals/transactions/{ref}` | 200 | Historical counterfactuals for a transaction |
| `POST /api/investigations` | 201 | Create an investigation for a transaction |
| `GET /api/investigations/{id}` | 200 | Investigation metadata |
| `POST /api/investigations/{id}/events` | 201 | Append an investigation event |
| `GET /api/investigations/{id}/timeline` | 200 | Append-only event timeline |
| `GET /api/investigations/{id}/decision-replay` | 200 | Replay for the investigation's transaction |
| `GET /api/investigations/{id}/evidence` | 200 | Evidence graph for the investigation's transaction |
| `GET /api/investigations/{id}/summary` | 200 | Composed summary (investigation + decision + simulations + counterfactuals + evidence + disagreement) |

### Errors

`ApiExceptionHandler` (`@RestControllerAdvice`) maps:
- `AnalyticsValidationException` → **400** `{code, message}`
- `AnalyticsNotFoundException` → **404**
- `AnalyticsMlUnavailableException` → **503** (`MlInferenceClient.CLIENT_UNAVAILABLE` surfaces here; internal ML errors stay `503` distinguished by `cause`)
- everything else → **400/500** generic, **never** exposing stack traces.

---

## Transactional Boundary Rules (DB / external calls)

- The pipeline must never hold a DB transaction across the ML call. Analytical services follow the same rule:
  - `DecisionReplayService`, `PolicyLabService.simulate`, `CounterfactualService.list` are read-only or short-lived.
  - `CounterfactualService.analyze` is **not** `@Transactional`: baseline loading uses a read-only `TransactionTemplate`, the ML call happens outside any transaction, and the result is persisted via `repo.save` in its own short transaction. If the ML call fails, **nothing** is persisted.
- `simulateBatch` does not hold one long transaction: each transaction is loaded under a short read-only transaction, simulated, and persisted independently; one failure reports error details without rolling back or re-scoring unrelated rows.

---

## Testing

- `DisagreementAnalysisTest` — pure classifier, all four categories + boundary cases.
- `PolicyLabServiceTest` — spec acceptance scenarios (0.72/0.75→ALLOW, 0.6/0.4→BLOCK, 0.82/0.8→REVIEW), immutability, batch, investigation attachment + rollback on mismatch.
- `CounterfactualServiceTest` — validation-before-ML (`verify(mlClient, never())` for bad edits), ML-failure → nothing persisted, decision flips, lossless original→modified round-trip.
- `DecisionReplayServiceTest` — replay contents, mutation-free read, rule recovery from evidence.
- `InvestigationApiTest` — create/get/events/timeline/summary, unknown txn → 404, invalid priority → 400, simulation recorded into summary.
- `Phase4EndToEndTest` — the spec's `txn-demo-001` story end-to-end with **row-count immutability assertions** across all production tables.

Full suite: **103 tests green** (67 Phase 1–3 regression + 36 Phase 4).