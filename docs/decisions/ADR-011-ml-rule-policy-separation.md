# ADR-011 — ML / Rule / Policy Separation

**Status:** Accepted

**Context:** Collapsing risk assessment, explicit rules, and business action into one service hides reasoning and prevents independent evolution.

**Decision:** Separate:
- ML → risk score + structured factors (model-driven)
- Rule engine → explicit findings (deterministic)
- Policy evaluator → business action (ALLOW/REVIEW/BLOCK) using persisted thresholds
- Evidence → explanation (graph of inputs)

**Consequences:** Each independently testable, versioned, explainable; disagreement detection (ML low + rules high) preserved without altering score.
