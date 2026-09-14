# Investigation Workspace — Frontend (Phase 5)

The risk investigation product layer: a React + Vite + TypeScript single-page app
in `frontend/` that consumes the Phase 1-4 backend. It is a **pure consumer** —
it renders persisted analytics and drives the analytics forms, but computes no
risk/decision/policy logic of its own (ADR-018).

## Stack

- React 19, Vite 8, TypeScript 5.9 (strict; `noUncheckedIndexedAccess`,
  `exactOptionalPropertyTypes`, `noImplicitOverride`)
- Tailwind CSS v4 (CSS-first, `@tailwindcss/vite`), ink design tokens in
  `src/index.css`
- react-router-dom 7 (deep-linkable routes)
- Vitest + Testing Library + jest-dom for contract tests
- No Redux, no global state — components fetch what they display
- No Playwright: component tests + the documented manual demo flow (§ demo below)

## Running locally

```bash
# backend on :8080 (and ml-risk-service on :8001 if you want live inference)
cd backend && mvn spring-boot:run

cd frontend
npm install
npm run dev          # http://localhost:5173, /api and /internal proxied to :8080
```

One-time demo seeding is automatic via Flyway `V11__demo_fixtures.sql`
(`txn-demo-001` ₹12,000, `txn-demo-002` ₹150, `txn-demo-003` ₹900,000 — all
`RECEIVED`). Process a transaction from its workspace ("Process transaction")
to produce the full decision lineage through the pipeline.

## Routes

| Route | Page | Data fetched |
|-------|------|--------------|
| `/` | Transaction lookup | `GET /api/transactions/{ref}` |
| `/transactions/:reference` | Transaction workspace | overview, decision replay, investigations list, process |
| `/investigations` | Investigations list | `GET /api/investigations` |
| `/investigations/:id` | Investigation workspace | metadata, summary, timeline, evidence, decision replay |

Refreshing any deep link restores state from the backend — nothing is lost.

## API mapping (frontend → backend)

All endpoints are consumed as-is; `src/api/types.ts` mirrors the backend records
field-for-field. The workspace layout follows the evidence hierarchy:

| Section | Source |
|---------|--------|
| Decision header (ACTUAL) | `DecisionReplay.decision` / `InvestigationSummary.decision` |
| Risk score + factors | `DecisionReplay.riskScore/riskFactors` (or summary) |
| Triggered rules + disagreement | `DecisionReplay.triggeredRules/disagreement` |
| Policy evaluation (threshold scale) | `DecisionReplay.policy` + `riskScore.score` |
| Decision replay (historical) | `GET /api/transactions/{ref}/decision-replay` |
| Evidence lineage | replay `evidence` / `GET /api/investigations/{id}/evidence` |
| Timeline | investigation events + simulation/counterfactual list endpoints |
| Policy lab (SIMULATED) | `POST /api/policy-lab/simulate` |
| Counterfactuals | `GET /api/counterfactuals/features`, `POST /api/counterfactuals` |
| Open investigation | `POST /api/investigations` |
| Add note event | `POST /api/investigations/{id}/events` |
| Process transaction (demo) | `POST /api/transactions/{ref}/process` (thin facade) |

## Actual vs hypothetical design

Every analytical artifact is stamped so the reader can never mistake re-scoring for
the production decision:

- **ACTUAL** — the persisted production decision (decision header)
- **SIMULATED** — policy-lab runs; explicitly "NOT A PRODUCTION POLICY CHANGE";
  no apply-to-production action exists
- **COUNTERFACTUAL** — hypothetical model analysis; always shown with the
  backend-provided disclaimer; reset restores the recorded feature snapshot, never
  mutates it
- **HISTORICAL** — decision replay, framed as a read-only reconstruction

Severity is never colour-only: badges carry text labels, dots, and explanatory
`title` attributes; contrast follows the ink palette.

## Demo scenario (spec §71)

1. `txn-demo-001` (₹12,000) → process → REVIEW, score 0.60.
2. Policy lab review 0.75 / block 0.90 → simulated ALLOW (decision changed).
3. Counterfactual `transaction_amount` → 60,000 → hypothetical BLOCK (Δ +0.30).
4. Open an investigation (HIGH, note) → Add a note → refresh: persisted.
5. Production records (feature snapshot, risk score, decision, evidence) are
   byte-identical before/after the lab. The narrative numbers are guaranteed by
   the mocked-ML E2E coverage in `backend/src/test/.../Phase4EndToEndTest` and
   the frontend contract fixtures (`src/test/fixtures.ts`); with live inference
   the actual score depends on the trained model.

## Tests

- `npm run typecheck` / `npm run lint` / `npm run build` must be green.
- `npm test`: contract tests assert the UI renders the backend value verbatim
  (e.g. "API says REVIEW → UI shows REVIEW"), never recomputes it: decision
  rendering, risk score + factors, rules, policy thresholds & robustness, evidence
  layering, timeline ordering, policy-lab validation + request/result,
  counterfactual request body + result + disclaimer, investigation create,
  error mapping (404/400/503/500/network), empty states.

## Historical integrity

The frontend has no write path to production artifacts. All UI-conducted analyses
(stimulations, counterfactuals, investigation events) persist as append-only
analytics artifacts and cannot alter `Transaction`, `DecisionRecord`, `RiskScore`,
`FeatureSnapshot`, `DecisionPolicy`, or the evidence graph (ADR-016/017).