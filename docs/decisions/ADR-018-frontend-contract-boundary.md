# ADR-018: Frontend as a pure consumer of the analytics API

- **Status:** accepted (Phase 5)
- **Date:** 2026-09-14
- **Deciders:** frontend + analytics engineering
- **Technical story:** Phase 5 must deliver the Investigation Workspace UI without
  duplicating any Phase 1-4 backend logic.

## Context

The platform has a feature-complete decision analytics backend (decision replay,
evidence graph, disagreement, policy lab, counterfactuals, investigation workflow)
exposed over `/api/...` (Phase 4, see `docs/architecture/PHASE-4-ANALYTICS.md`).
There was no frontend. Phase 5 builds the investigative workspace from scratch.

The tempting shortcut — re-deriving risk categories or decision bands in TypeScript
(e.g. `if (score >= blockThreshold) ...`) — corrupts the single-source-of-truth
principle and would drift from the persisted policy evaluation.

## Decision

- Build a **React + Vite + TypeScript** single-page app in `frontend/` that is a
  **pure consumer** of the public API.
- The frontend **renders** backend values verbatim; it never recomputes decisions,
  rule findings, disagreement, counterfactual math, or policy outcomes.
- The only "validation" the UI performs is input-shape validation for the API
  contract (e.g. `0 < review < block < 1` for a simulated policy), which mirrors
  the backend's contract and is send-before-compute, not decision logic.
- Policy-lab actions are simulations only — there is no apply-to-production action.
- Counterfactuals are displayed with the backend-provided hypothetical disclaimer;
  the recorded feature snapshot is never mutated by the UI.
- Mock data is never used as UI data; deterministic demo fixtures live in
  `backend/.../V11__demo_fixtures.sql` and flow through the real API.

## Consequences

- Positive: the API becomes the tested contract; TypeScript `src/api/types.ts`
  mirrors backend records 1:1; a new backend capability appears in the UI with no
  frontend logic change.
- Positive: Phase 6 (evidence-grounded LLM explanations) can be added as a backend
  capability consumed the same way.
- Negative: the UI cannot make offline decisions; it is only as rich as the API it
  consumes (this is the intended asymmetry).
- Risk: contributions re-deriving thresholds in TS would be rejected in review; a
  lint/test can assert no `riskScore >=`-style logic exists in `src/`.