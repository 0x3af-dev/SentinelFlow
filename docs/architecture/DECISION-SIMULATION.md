# Decision Simulation — Policy Lab

Policy Lab answers *"what would this already-decided transaction have been if the policy thresholds had been different?"*

It is a **pure what-if analysis against the production decision**. It changes nothing.

---

## Semantics

The production decision for a transaction is reproduced exactly by `ThresholdDecision`, which replicates `PolicyEvaluator`'s decision logic **read-only**:

```
risk_score < review  → ALLOW
risk_score < block   → REVIEW
otherwise            → BLOCK
```

Production defaults (seeded `fraud-policy` v1): review `0.5`, block `0.85`.

The simulation computes the decision the production rule *would* have produced for the **same persisted risk_score** if `review_threshold` and `block_threshold` had been the submitted values. Because `ThresholdDecision` is byte-for-byte the same logic, feeding it the production thresholds reproduces the recorded decision — the acceptance cases below assert exactly that.

`change_type` is derived from the production vs simulated decision:

| actual → simulated | change_type |
|--------------------|-------------|
| same | `UNCHANGED` |
| ALLOW → REVIEW/BLOCK | `MORE_RESTRICTIVE` |
| REVIEW → ALLOW | `MORE_PERMISSIVE` |
| REVIEW → BLOCK | `MORE_RESTRICTIVE` |
| BLOCK → REVIEW/ALLOW | `MORE_PERMISSIVE` |

`decisionChanged` is simply `changeType != UNCHANGED`.

---

## Spec Acceptance Cases (covered by tests)

| risk_score | review | block | production | simulated | change |
|-----------|--------|-------|------------|-----------|--------|
| 0.72 | 0.75 | 0.9 | REVIEW | **ALLOW** | MORE_PERMISSIVE |
| 0.6 | 0.4 | 0.9 | ALLOW | **BLOCK** | MORE_RESTRICTIVE |
| 0.82 | 0.8 | 0.9 | REVIEW | **REVIEW** | UNCHANGED |

### Validation (all before any work)
- transaction must exist → `AnalyticsNotFoundException` (404)
- `review_threshold` must be strictly between 0 and 1
- `block_threshold` must be strictly between review and 1 (i.e. `review < block`)
- `policy_name` must be `fraud-policy` (only one policy is realistically simulated; the check keeps the audit trail honest)
- a transaction that has never been scored through the pipeline has no feature snapshot / risk score / decision → `AnalyticsNotFoundException`
- `changed=true` implicitly ignored, `performed_by` optional

---

## The Production Evaluator Is Untouched

`PolicyEvaluator` is not modified and is never invoked by Phase 4. Decision simulation semantics live in `ThresholdDecision` (`analytics/shared`), which owns the three-way comparison and the change-type derivation. This keeps the single source of truth for *production* decisions in the Decision domain and the *analytical* replica isolated under test.

---

## Batch Simulation

`POST /api/policy-lab/simulate-batch` accepts a list of transaction references. Each is validated and simulated **independently**:
- one transaction missing → it reports `notFound`, the others are still simulated;
- each simulation persists its own `policy_simulations` row;
- response `simulations[]` mirrors single-simulation shape per transaction.

Batch never operates on the same transaction twice in one request (rejected as validation error).

---

## Persistence & Investigation Hook

Every simulation persists an append-only `policy_simulations` row (see `PHASE-4-ANALYTICS.md`).

An optional `investigationId` attaches the simulation to an investigation. The attachment validates that the investigation belongs to the **same transaction**; on mismatch the simulation record and the emitted event are both rolled back together (no orphaned evidence, no phantom audit).

Recording emits the timeline event `POLICY_SIMULATION_EXECUTED` (actor `SYSTEM`, payload `{simulation_id, policy_name, review_threshold, block_threshold, actual_decision, simulated_decision, change_type}`) via `InvestigationEventPublisher`.

## Endpoints
- `POST /api/policy-lab/simulate` — 200
- `POST /api/policy-lab/simulate-batch` — 200
- `GET /api/policy-lab/transactions/{ref}/simulations` — 200, newest first