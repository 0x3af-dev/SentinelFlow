# ADR-017 — Read-Only Analysis Guarantees

**Status:** Accepted

**Context:** Phase 4 is analysis layered on top of a phase-proven pipeline whose core value is an *immutable, replayable decision record*. Analytical features must never be able to mutate the production record — neither by API misuse nor by subtle code-path accidents (e.g. JPA dirty-checking a managed entity accidentally loaded during an analysis call).

**Decision:** Analysis guarantees are enforced at three levels:

1. **Package boundaries & service contracts** — analytical services operate only on their own append-only artifacts plus reads through repositories; they never pass through `Pipeline`/`PolicyEvaluator`/`RuleEngine` mutating entry points.
2. **Transactional hygiene** — the pipeline never holds a DB transaction across the ML call; analytical services follow the same rule. `CounterfactualService.analyze` is non-`@Transactional`: the baseline is loaded in a short, read-only `TransactionTemplate`, the ML call runs outside any transaction, and persistence is a separate short `save`. No managed `Transaction`/`RiskScore`/`FeatureSnapshot`/`DecisionRecord` entity is ever reached through a write-spawning path.
3. **Tests** — `Phase4EndToEndTest` snapshots row counts **and** content of `feature_snapshots`, `risk_scores`, `decision_records`, `evidence_nodes`, `evidence_edges` around every analytical action and asserts exact equality afterward; individual service tests assert the same locally (e.g. replay test asserts the loaded snapshot's `created_at` is untouched).

**Alternatives considered:**
- Relying on "we just don't write" discipline — rejected: too subtle, unverifiable.
- Making every analytical read `@Transactional(readOnly = true)` — used where beneficial (replay), but insufficient alone: a readOnly outer transaction does not prevent a nested opportunistic write, and it must **never** wrap the ML call.

**Consequences:** A defensive default where the analysis layer structurally cannot poison the decision record. The immutability assertions in the test suite are the enforcement gate at the seam between analysis and production data.

**References:** ADR-004 (historical data), ADR-008 (pipeline tx the pipeline never holds during ML), ADR-015, ADR-016.