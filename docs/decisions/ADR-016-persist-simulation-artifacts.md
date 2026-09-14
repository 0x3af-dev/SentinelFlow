# ADR-016 — Persist Simulation & Counterfactual Artifacts (Append-Only)

**Status:** Accepted

**Context:** Decision simulations and counterfactual analyses are the spec's core *"what if"* evidence. The spec emphasizes reproducibility and auditability of analytical claims. Early design leaned ephemeral (compute on demand, persist nothing), justified by "they are hypotheticals, not facts."

**Decision:** **Persist.** Both artifacts are stored in append-only tables introduced by Flyway `V10` (`policy_simulations`, `counterfactual_analyses`):

- Every simulation/counterfactual is a reviewable artifact: exactly which decision, which feature snapshot, which model, which thresholds/modifications, by whom, and when — all FK-linked to the immutable production records it references.
- Rows are insert-only; no update path exists (entities expose no mutators used post-persist; `updated_at` is schema convention only).
- Persisting is what makes an investigation summary able to show a *history* of analyses per transaction (regression from a change policy later), and what lets an audit answer "who simulated what and what did they conclude." That matches the spec's reproducibility/auditability emphasis more strongly than the ephemeral alternative.

**Alternatives considered:**
- Ephemeral in-memory results with replay on demand — rejected: analysis history silently disappears; audit "what-if trail" lost; investigation summary could not list past simulations/counterfactuals (spec explicitly wants that context).
- Storage in the evidence graph (nodes/edges) — rejected: evidence stores *what happened* (provenance); hypothetical artifacts are *what-if*, mixing them corrupts provenance semantics. Kept separate, keyed to the same transaction.
- No FK to the immutable records — rejected: artifacts must be traceable to their exact inputs.

**Consequences:** Two new append-only tables, all inserts short-transaction, no performance impact on the write path. Analysis rows are never edited — corrections are new rows (still accurate to what actually ran). Disk cost is negligible relative to the audit value.

**References:** `docs/architecture/PHASE-4-ANALYTICS.md` (schema), ADR-015, ADR-017.