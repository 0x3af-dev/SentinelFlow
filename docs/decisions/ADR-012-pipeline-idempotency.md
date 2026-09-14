# ADR-012 — Pipeline Idempotency

**Status:** Accepted

**Context:** Duplicate processing (retry, replay, Kafka at-least-once) must not create contradictory decisions.

**Decision:** Idempotency via `transaction_id + model_version + feature_schema_version + policy_version` lineage. `DecisionService` checks `findByTransactionIdAndRiskScoreIdAndPolicyId` before creating. Evidence nodes/edges checked before creation. Historical records retained on rescoring (new snapshot/risk/decision), not overwritten.

**Consequences:** Safe retry, replay, and future Kafka integration without duplicate prevention via random state.
