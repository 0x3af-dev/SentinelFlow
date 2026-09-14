# ADR-015 — Policy Simulation Is a Read-Only Replica, Not an Evaluation

**Status:** Accepted

**Context:** Policy Lab (Phase 4) must show what a transaction's *already-recorded* decision would have been under different thresholds. It can never change the production decision, and it must not diverge from production semantics or the tool is actively misleading.

**Decision:**

- Production evaluation stays owned by `PolicyEvaluator` (Decision domain, `fraud-policy/v1` active) and is **never invoked, modified, or shadowed** by Phase 4.
- What-if semantics live in `ThresholdDecision` (`analytics.shared`), a pure function reproducing the exact three-way logic (`score < review → ALLOW; < block → REVIEW; else BLOCK`) plus `changeType` derivation (`UNCHANGED` / `MORE_PERMISSIVE` / `MORE_RESTRICTIVE`).
- An explicit acceptance test feeds production thresholds into `ThresholdDecision` and asserts it reproduces the recorded decision, so drift between the replica and production logic fails loudly.
- Simulations persist to an append-only `policy_simulations` table (see ADR-016 for the shared justification) keyed to the immutable decision and feature snapshot.

**Alternatives considered:**
- Call the real `PolicyEvaluator` with mutated policy state — rejected: it looks up the active policy from the DB, so it cannot evaluate arbitrary thresholds without polluting the policy registry; and it would create a dependency from the analysis layer into production decision code.
- Ephemeral (in-memory) simulation results — rejected: reproducibility/auditability (see ADR-016).

**Consequences:** A small, tested replica instead of reuse — acceptable because the logic is three comparisons. Any future change to production thresholds semantics requires updating the replica *and* the acceptance test, which is the point: drift cannot happen silently.

**References:** `docs/architecture/DECISION-SIMULATION.md`, ADR-016.