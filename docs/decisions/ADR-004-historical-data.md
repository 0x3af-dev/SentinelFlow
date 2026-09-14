# ADR-004: Historical Data Immutability

## Context
SentinelFlow must preserve the exact state of risk decisions, model predictions, policy evaluations, and investigation history for audit, replay, counterfactual analysis, and regulatory compliance.

## Decision
**Never overwrite historical records.** Implement append-only patterns for:
- Model versions: New version = new row (UNIQUE model_name + version)
- Policy versions: New version = new row (UNIQUE policy_name + version)
- Feature snapshots: New evaluation = new row (never UPDATE)
- Decision records: Immutable once created
- Evidence nodes/edges: Append-only
- Investigation events: Append-only (status changes create new event, not UPDATE)
- Audit logs: Append-only

Database constraints enforce uniqueness on version pairs. Application logic does not issue UPDATEs on these tables (except `updated_at` on mutable entities like `users`, `investigations`).

## Alternatives Considered
1. **Mutable with audit table** — Update in place, log changes to audit
   - Pros: Simpler queries (current state in main table)
   - Cons: Audit table can drift, hard to reconstruct exact past state, audit often incomplete
2. **Event sourcing** — Store all state changes as events
   - Pros: Complete history, replayable
   - Cons: Complex, overkill for Phase 1, steep learning curve
3. **Append-only with current-state views (chosen)** — Never UPDATE, use views or queries for "latest"
   - Pros: Simple, complete history, easy replay, natural fit for SQL
   - Cons: More storage, "latest" requires query (mitigated by indexes on `created_at` / `activated_at`)

## Reasoning
Financial risk decisions require full traceability:
- Regulator asks "Why was this transaction BLOCKED?" → Must show exact model version, policy version, feature values, risk score at decision time
- Data scientist asks "How would v2 model have scored last month's transactions?" → Feature snapshots must be intact
- Analyst asks "What policy was active on 2026-09-01?" → Policy versions with activated_at/retired_at answer this

Append-only is the simplest correct approach. Storage is cheap; regulatory fines are not.

## Tradeoffs
- Accept: More rows (storage growth)
- Accept: "Current version" queries need `WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1`
- Gain: Perfect historical fidelity, trivial replay, regulatory compliance

## Consequences
- Migrations add CHECK constraints where applicable
- No UPDATE statements on versioned/historical tables in application code
- Seed data uses `ON CONFLICT DO NOTHING` for idempotency
- Future: Materialized views for "current model/policy" if query performance demands

## Status
Accepted — implemented in Phase 1