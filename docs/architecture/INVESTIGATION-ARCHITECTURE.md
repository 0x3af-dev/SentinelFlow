# Investigation Architecture & Analysis Services

Phase 4's investigation layer is a **read-only, analysis-forward** foundation: it composes the persisted decision lineage (replay), the evidence graph, the simulation/counterfactual history, and model-vs-rule disagreement into a single investigation context for an analyst. Investigations never mutate the transaction or its decision.

---

## Investigation Service (`com.sentinelflow.investigation.service`)

### `InvestigationApplicationService`
Facade for creating and reading investigations and their analytical context.

- **create** — validates the transaction exists (404 otherwise); defaults status `OPEN`, priority `MEDIUM`; generates an `INV-` style reference; creates the investigation and the initial `INVESTIGATION_CREATED` event in one transaction. Returns `InvestigationMetadata`.
- **get(id)** — `InvestigationMetadata` (status, priority, assignee, resolution, event count).
- **addEvent(id, req)** — appends an event to the investigation's append-only timeline (`INVESTIGATION_CREATED`, `POLICY_SIMULATION_EXECUTED`, `COUNTERFACTUAL_EXECUTED`, `EVIDENCE_ADDED`, `NOTE_ADDED`, `STATUS_CHANGED`, `DECISION_CHANGED` are known types; any string is accepted but the timeline is append-only — nothing is ever editable). Payload is a JSON object.
- **timeline(id)** — all events oldest-first, each with `eventType`, `actor`, `timestamp`, `payload`.
- **decisionReplay(id)** — the replay for the investigation's transaction (see below).
- **evidence(id)** — the bounded evidence graph for the investigation's transaction.
- **summary(id)** — a composed, read-only picture:
  `{investigation, decision, featureSnapshot, riskScore, triggeredRules, policy, disagreement, policySimulations, counterfactuals, evidenceNodeCount, evidenceEdgeCount}`.
  Empty collections (never null) when nothing was recorded yet.

Validation rules: `priority` ∈ {LOW, MEDIUM, HIGH} (400 otherwise); `resolution` only meaningful via events for now; unknown investigation id → 404.

### `InvestigationEventPublisher`
Slices `InvestigationEvent` recording out of the analytical services so policy-lab and counterfactual code paths can emit events without owning investigation logic. It validates the event's transaction matches the investigation's transaction — mismatches raise `AnalyticsValidationException` so the caller can roll back its own record.

---

## Decision Replay (`analytics.replay.DecisionReplayService`)

Reconstructs, read-only, everything the pipeline recorded for one transaction:

- **transaction** (reference, status, amount, currency, channel, type, timestamp)
- **feature snapshot** (latest, immutable)
- **model** (name/version/algorithm) + prediction latency
- **risk score** (score, label)
- **risk factors** (all persisted)
- **triggered rules** — recovered from the evidence graph: evidence nodes of type `RULE_RESULT` linked by `CONTRIBUTES_TO` edges that point at the risk-score evidence node (bounded; no recursion)
- **policy** (the policy that governed the decision) **+ decision** (final decision, reason)
- **evidence** summary (node/edge counts)
- **disagreement** classification

All lookups are scoped to the transaction and read-only. Replaying never stores anything.

## Disagreement Analysis (`DisagreementInfo` in `analytics.dto`)

Pure, deterministic classification of *model prediction vs rule findings*, adapted from the documented model/rule semantics (implemented as the static factory `DisagreementInfo.of(modelPrediction, modelScore, triggeredRules)`):

- model HIGH if `prediction == "HIGH"` **or** `score >= 0.60`; else model LOW
- rule HIGH if any triggered rule has severity `HIGH`

| model | rules | category |
|-------|-------|----------|
| HIGH | HIGH | `ML_HIGH_RULE_HIGH` (model and rules agree — highest risk) |
| HIGH | LOW  | `ML_HIGH_RULE_LOW` |
| LOW  | HIGH | `ML_LOW_RULE_HIGH` |
| LOW  | LOW  | `ML_LOW_RULE_LOW` |

Returned in both the replay response and the investigation summary. It is descriptive only — it drives no decision and no re-scoring.

---

## Evidence Graph (`EvidenceGraphCollector` in `analytics.shared`)

`EvidenceGraphCollector` returns a bounded, read-only projection of the persisted evidence graph for a transaction:
- all evidence nodes whose `entityType`/`entityId` relate to the transaction or its lineage,
- all edges between those nodes,
- counts.

Explicitly NOT implemented: recursive graph traversal, path queries, graph algorithms — out of scope for Phase 4 (matches the roadmap; no Neo4j).

---

## Endpoints

| Endpoint | Status |
|----------|--------|
| `POST /api/investigations` | 201 |
| `GET /api/investigations/{id}` | 200 |
| `POST /api/investigations/{id}/events` | 201 |
| `GET /api/investigations/{id}/timeline` | 200 |
| `GET /api/investigations/{id}/decision-replay` | 200 |
| `GET /api/investigations/{id}/evidence` | 200 |
| `GET /api/investigations/{id}/summary` | 200 |

See `PHASE-4-ANALYTICS.md` for the shared error contract.