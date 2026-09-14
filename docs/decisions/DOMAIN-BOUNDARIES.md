# SentinelFlow Phase 1 & 2 — Domain Boundaries

This document defines the ownership boundaries between modules in the modular monolith. Each module owns its entities, repositories, and domain logic. Cross-module access goes through service-layer APIs (added in Phase 2), not direct entity references.

**Phase 2 adds:** enrichment, feature, ml, rule, policy, pipeline, evidence service boundaries (see below).
**Phase 3 adds:** Kafka messaging/outbox boundary (§17) — transport only, no business logic.
**Phase 4 adds:** analytics (replay, policy lab, counterfactual, disagreement, evidence graph) + read-only investigation application boundary (§18, §19).
**Phase 5 adds:** React investigative workspace — pure consumer of the analytics API (§20).
**Phase 6 adds:** AI investigator — evidence-grounded, read-only LLM assistant gated by strict tool budgets, validation, and audit trail (§21).

---

## 1. Identity Domain
**Package:** `com.sentinelflow.identity`

### Responsibility
User, device, and location identity management. Synthetic identities only — no authentication, no passwords, no PII beyond what's needed for risk analysis.

### Owned Entities
- `User` — Core identity (external_reference, display_name, status)
- `Device` — User devices (device_reference, type, platform, seen timestamps)
- `Location` — User geographic locations (country, region, city, lat/long)

### Repositories
- `UserRepository`
- `DeviceRepository`
- `LocationRepository`

### Important Dependencies
- **Referenced by:** Transaction (user_id, device_id, location_id), Investigation (via transaction)

### Does NOT Own
- Authentication/authorization (future security phase)
- KYC/identity verification (future compliance phase)
- Passwords, tokens, secrets

---

## 2. Transaction Domain
**Package:** `com.sentinelflow.transaction`

### Responsibility
Financial transaction processing lifecycle — ingestion, enrichment, status tracking. Does NOT make risk decisions.

### Owned Entities
- `Merchant` — Merchant catalog (external_reference, name, category, status)
- `Transaction` — Core transaction (amount, currency, type, channel, status, timestamps)

### Repositories
- `MerchantRepository`
- `TransactionRepository`

### Important Dependencies
- **References:** Identity (User, Device, Location via FK)
- **Referenced by:** Risk (transaction_id), Decision (transaction_id), Evidence (entity_id), Investigation (transaction_id), Audit (entity_id)

### Does NOT Own
- Risk scoring (Risk domain)
- Final decisions (Decision domain)
- Fraud classification (Decision domain)
- Enrichment pipeline (Phase 2)

---

## 3. Risk Domain
**Package:** `com.sentinelflow.risk`

### Responsibility
ML model metadata, feature engineering artifacts, risk scores, and risk factors. Purely analytical — no decisions.

### Owned Entities
- `ModelVersion` — Immutable model metadata (name, version, algorithm, schema, artifact ref, status)
- `FeatureSnapshot` — Immutable feature vectors per evaluation (JSONB features, schema version)
- `RiskScore` — Model prediction (score 0-1, prediction label, latency, model reference)
- `RiskFactor` — Individual risk contributors (type, severity, source)

### Repositories
- `ModelVersionRepository`
- `FeatureSnapshotRepository`
- `RiskScoreRepository`
- `RiskFactorRepository`

### Important Dependencies
- **References:** Transaction (transaction_id), ModelVersion (model_version_id)
- **Referenced by:** Decision (risk_score_id), Evidence (entity_id)

### Does NOT Own
- Model training/serving (Phase 2 Python service)
- Feature computation (Phase 2 enrichment)
- Decision making (Decision domain)
- Policy evaluation (Decision domain)

---

## 4. Decision Domain
**Package:** `com.sentinelflow.decision`

### Responsibility
Decision policies and final risk decisions. Connects risk output to business action.

### Owned Entities
- `DecisionPolicy` — Immutable policy versions (configuration JSONB, thresholds, status)
- `DecisionRecord` — Final decision (ALLOW/REVIEW/BLOCK, reason, lineage to risk_score + policy)

### Repositories
- `DecisionPolicyRepository`
- `DecisionRecordRepository`

### Important Dependencies
- **References:** Transaction (transaction_id), Risk (risk_score_id), DecisionPolicy (policy_id)
- **Referenced by:** Evidence (entity_id), Investigation (via transaction), Audit (entity_id)

### Does NOT Own
- Rule engine (Phase 2)
- Policy simulation/lab (implemented Phase 4 in `analytics.policy` — read-only replica, never evaluates production policy)
- ML model selection (Risk domain)

---

## 5. Evidence Domain
**Package:** `com.sentinelflow.evidence`

### Responsibility
Persisted evidence graph — nodes and typed edges representing the decision provenance chain.

### Owned Entities
- `EvidenceNode` — Typed artifacts (TRANSACTION, DEVICE, FEATURE, MODEL_PREDICTION, RISK_FACTOR, DECISION, etc.)
- `EvidenceEdge` — Typed relationships (USED_DEVICE, GENERATED_FEATURE, CONTRIBUTES_TO, GOVERNED_BY, etc.)

### Repositories
- `EvidenceNodeRepository`
- `EvidenceEdgeRepository`

### Important Dependencies
- **References (polymorphic):** Any domain entity via `entity_type` + `entity_id`
- **Referenced by:** Investigation UI (future), Audit (correlation)

### Does NOT Own
- Graph visualization (Phase 2 React UI)
- Graph algorithms (PageRank, community detection — Phase 3)
- Evidence collection (automated via decision pipeline, Phase 2)

---

## 6. Investigation Domain
**Package:** `com.sentinelflow.investigation`

### Responsibility
Human investigation workflow — cases, events, audit trail.

### Owned Entities
- `Investigation` — Case (status, priority, assignee, resolution, notes)
- `InvestigationEvent` — Append-only event log (event_type, actor, payload)
- `AuditLog` — Generic entity audit trail (actor, action, before/after state, correlation)

### Repositories
- `InvestigationRepository`
- `InvestigationEventRepository`
- `AuditLogRepository`

### Important Dependencies
- **References:** Transaction (transaction_id)
- **Referenced by:** Evidence (INVESTIGATION node_type)

### Does NOT Own
- Investigation UI (future)
- Case assignment rules (future)
- Analyst workload management (future)
- Authentication/authorization (future security phase)
- Analytical composition/read-only application service (implemented Phase 4 in `com.sentinelflow.investigation.service`)

---

## 7. Model Domain (subset of Risk)
**Package:** `com.sentinelflow.risk` (ModelVersion, FeatureSnapshot)

### Responsibility
Model registry and feature snapshots — separated conceptually from scoring/factors.

### Owned Entities
- `ModelVersion`
- `FeatureSnapshot`

### Does NOT Own
- Model training (Phase 2)
- Model serving (Phase 2)
- Feature store (Phase 2 — may use Feast)

---

## 8. Policy Domain (subset of Decision)
**Package:** `com.sentinelflow.decision` (DecisionPolicy)

### Responsibility
Policy version registry and configuration.

### Owned Entities
- `DecisionPolicy`

### Does NOT Own
- Policy authoring UI (future)
- Policy simulation (implemented Phase 4 in `analytics.policy`)
- Rule definitions (Phase 2 — separate from policy config)

---

## 9. Audit Domain (subset of Investigation)
**Package:** `com.sentinelflow.investigation` (AuditLog)

### Responsibility
System-wide entity audit trail.

### Owned Entities
- `AuditLog`

### Does NOT Own
- Business event streaming (Phase 3 Kafka `com.sentinelflow.kafka`)
- SIEM integration (Phase 3)

---

## Cross-Module Dependency Graph

```
identity
    │
    ▼
transaction ◄── merchant (internal)
    │
    ├──► risk ◄── model_version
    │         │
    │         ├──► feature_snapshot
    │         ├──► risk_score
    │         └──► risk_factor
    │
    ├──► decision ◄── decision_policy
    │         └──► decision_record
    │
    ├──► evidence (polymorphic refs to all above)
    │
    └──► investigation ◄── transaction
              ├──► investigation_event
              └──► audit_log (polymorphic refs to all)
```

## 10. Enrichment Domain (Phase 2)
**Package:** `com.sentinelflow.enrichment`
**Responsibility:** Read-only transaction context enrichment (user, device, location, merchant). Does NOT mutate transaction.
**Owned:** `TransactionEnricher` interface + `TransactionEnricherImpl`, no entities (uses Identity/Transaction entities via repositories)
**Does NOT Own:** Feature computation, ML, decision

## 11. Feature Domain (Phase 2)
**Package:** `com.sentinelflow.feature`
**Responsibility:** Deterministic feature computation from EnrichmentContext → FeatureSet (fs-v1), snapshot persistence
**Owned:** `FeatureComputationService`, `FeatureSnapshotService`
**Does NOT Own:** ML inference, policy

## 12. ML Domain (Phase 2)
**Package:** `com.sentinelflow.ml` (Java client) + `ml-risk-service/` (Python)
**Responsibility:** Java client constructs request, validates response, maps to MlPrediction; Python service loads model, validates features, predicts risk score 0..1, returns structured risk factors
**Owned:** `MlInferenceClient`, `MlInferenceRequest/Response`, Python `train.py`, `features.py`, `model.py`, `risk_factors.py`
**Does NOT Own:** Business policy, final decision, evidence
**Boundary:** ML service answers "How risky?" not "Should we block?"

## 13. Rule Domain (Phase 2)
**Package:** `com.sentinelflow.rule`
**Responsibility:** Deterministic rule evaluation (explicit conditions) → RuleResult
**Owned:** `Rule` interface, 5 rules (HighAmount, NewDevice, HighVelocity, NewLocation, CombinedSuspicion), `RuleEngine`
**Does NOT Own:** Policy evaluation, ML score modification

## 14. Policy Domain (Phase 2 extension)
**Package:** `com.sentinelflow.policy`
**Responsibility:** Policy evaluation using persisted `fraud-policy/v1` (review 0.5, block 0.85) → PolicyEvaluationResult
**Owned:** `PolicyEvaluator`
**Does NOT Own:** Rule engine, ML inference

## 15. Pipeline Domain (Phase 2)
**Package:** `com.sentinelflow.pipeline`
**Responsibility:** Orchestration only (load → enrich → features → snapshot → ML → risk → rules → policy → decision → evidence)
**Owned:** `TransactionIntelligencePipeline`
**Does NOT Own:** Domain logic (delegates to services)

## 16. Evidence Service (Phase 2)
**Package:** `com.sentinelflow.evidence.service`
**Responsibility:** Build connected evidence graph from pipeline result
**Owned:** `EvidenceService`
**Does NOT Own:** Decision calculation

## 17. Kafka Messaging (Phase 3)
**Packages:** `com.sentinelflow.kafka` (+ `com.sentinelflow.kafka.outbox`, `com.sentinelflow.kafka.attempt`), enqueue entry point in `com.sentinelflow.api`
**Responsibility:** Asynchronous **transport** only — deliver "process transaction" events reliably, idempotently, with bounded retry and dead-letter capture. Contains no business/decision logic.
**Owned:**
- Event contract (`TransactionProcessingEvent`, `KafkaTopics`)
- `TransactionEventConsumer` (routing only) + `TransactionEventProcessor` (validation, attempt tracking, pipeline delegation)
- `KafkaProducerConfig`, `KafkaConsumerConfig`, `KafkaErrorConfig` (DLQ recoverer)
- `OutboxEvent`, `KafkaProcessingAttempt` (tables `outbox_events`, `kafka_processing_attempts`)
- `OutboxService` / `OutboxPublisher` (transactional outbox relay)
- `TransactionEventEnqueueController` (`/internal/kafka/transactions/{ref}/enqueue`)
**Depends on:** `TransactionIntelligencePipeline` (orchestration), `TransactionRepository` (existence check), `KafkaTopics` constants; DB via `KafkaProcessingAttemptRepository`, `OutboxEventRepository`
**Does NOT Own:** Risk scoring, rules, policy, decisions, evidence creation — everything it triggers lives in the Phase 2 pipeline. Kafka seeks are never the source of truth; the attempts/outbox tables are.
**Boundary rule:** Consumers must never mutate domain entities directly; all business effects go through the pipeline.

## 18. Analytics Domain (Phase 4)
**Packages:** `com.sentinelflow.analytics` (`replay`, `policy`, `counterfactual`, `dto`, `exception`, `shared`, `model`)
**Responsibility:** Read-only decision analytics layered over the Phase 1–3 record: decision replay, policy what-if simulation, counterfactual feature what-if (re-scored via the ML client, hypothetical only), model-vs-rule disagreement classification, and bounded evidence graph reads. Analysis **never** mutates production records and **never** evaluates or modifies the production policy.
**Owned:**
- `PolicySimulation`, `CounterfactualAnalysis` entities + repos (append-only, tables `policy_simulations`, `counterfactual_analyses`, Flyway V10)
- `PolicyLabService` (simulate + batch), `CounterfactualService`, `DecisionReplayService`, `EvidenceGraphCollector`, `ThresholdDecision` (read-only replica of production policy semantics), `DisagreementInfo.of` (classification)
- `CounterfactualFeatureRegistry` (bounded numeric feature whitelist for edits)
- API: `DecisionReplayController`, `PolicyLabController`, `CounterfactualController`, `ApiExceptionHandler`
**Depends on (read-only):** decision, risk, evidence, transaction, investigation (event hook), `MlInferenceClient` (counterfactual re-score)
**Does NOT Own:** production decision evaluation, production policy, rule engine, Kafka, anything that writes outside its two append-only tables
**Boundary rules:** ML call never wrapped in a DB transaction; `simulateBatch`/`list` never hold long transactions; no causal claims (counterfactual responses carry an explicit hypothetical disclaimer); simulation threshold validation (`0 < review < block < 1`) enforced before any work.

## 19. Investigation Application Domain (Phase 4)
**Packages:** `com.sentinelflow.investigation.service`
**Responsibility:** read-only, analysis-forward workflow facade over the Investigation domain — create investigations and compose their analytical context (decision replay, evidence, simulations, counterfactuals, disagreement) into `InvestigationSummary`, plus an append-only event timeline.
**Owned:** `InvestigationApplicationService`, `InvestigationEventPublisher` (+ `InvestigationController` in `com.sentinelflow.api`)
**Depends on:** Investigation domain entities (read/write of investigations only), Analytics (read-only)
**Does NOT Own:** investigation entity mutations authored by analysts with side effects on transactions — it never touches `Transaction`, `DecisionRecord`, `RiskScore`, `FeatureSnapshot`, or evidence.
**Boundary rule:** analyst events are append-only entries on `investigation_events`; analytical artifacts attach to investigations only when they belong to the investigation's transaction.

---

## 20. Frontend Investigative Workspace (Phase 5)
**Location:** `frontend/` (React + Vite + TypeScript)
**Responsibility:** present the Phase 1-4 backend as an investigation product. It is a **pure consumer**: it renders persisted analytics, drives the policy-lab simulation form, submits counterfactual request bodies, and records investigation events — all against the public `/api` contract. It never derives, recomputes, or predicts any risk/decision/rules/policy result.
**Owned:** the UI contract — routes, panels, forms, rendering, error presentation (`src/api/*` maps the backend DTOs one-to-one).
**Does NOT Own:** risk scoring, rule evaluation, policy evaluation, decision logic, counterfactual computation, evidence building, ML inference, or any mutation of `Transaction`, `DecisionRecord`, `RiskScore`, `FeatureSnapshot`, `DecisionPolicy`, or evidence records. It has no repository/entity access of its own.
**Boundary rules (spec §12):** no "if score >= threshold" style logic anywhere in TS; decision badges render the backend's decision verbatim; policy-lab thresholds are only validated for the form contract (`0 < review < block < 1`) before being sent — never used to decide; every simulation/counterfactual is labelled SIMULATED/COUNTERFACTUAL and carries the hypothetical disclaimer; actual vs hypothetical artifacts are visually distinct (Stamp components); absence of factors/rules/evidence is rendered as an explicit empty state, never as a safety claim.

## 21. AI Investigator Domain (Phase 6)
**Packages:** `com.sentinelflow.ai` (`prompt`, `gateway`, `tool`, `validation`, `config`, `model`, `repo`, `service`, `dto`, `exception`)
**Responsibility:** answer structured and free-form analyst questions about an already-decided transaction using **only the evidence already persisted** for that decision. It is a read-only assistant that never evaluates a policy, never scores a transaction, never mutates a decision, and never sits on the transaction critical path. Disabled by default; no provider, key, or model bean exists until explicitly enabled.
**Owned:**
- `AiInvestigationService` (orchestration: fail-fast when disabled → assemble kernel → budgeted generation → validation → audit)
- `PromptAssembler` (immutable system prompt: evidence taxonomy FACT/INFERENCE/HYPOTHESIS/UNKNOWN, authority hierarchy, "never decide" rules, untrusted-data containment)
- `AiGateway` / `SpringAiGateway` (provider boundary; failure classification)
- `InvestigationAiTools` / `ToolCallBudget` / `ToolViews` (read-only model surface, per-request budget, toolbar JSON views)
- `ExplanationValidator` (semantic backstop: evidence IDs resolve to the persisted graph; decision/score/policy match the persisted record; action-language rejected; hypothetical disclaimers mandatory)
- `AiInvestigationRun` (+ `ai_investigation_runs` Flyway V12) — append-only audit trail of every attempt, success and failure
- API: `AiInvestigationController.explain` / `runs`; AI exception codes `AI_UNAVAILABLE` (503) / `AI_RESPONSE_INVALID` (502) in `ApiExceptionHandler`
**Depends on (read-only through `InvestigationApplicationService`):** investigation context, decision replay, evidence graph, policy simulations, counterfactuals.
**Does NOT Own:** decision evaluation, policy evaluation, risk scoring, rule engine, ML inference, evidence creation, Kafka, transaction processing — and it cannot mutate any production record; its only write is its own audit table.
**Boundary rules:** the model is restricted to read-only tools whose outputs are validated before being surfaced; tool budget exhaustion or validation rejection yields a FAILED audit run and 502, never a partial answer; free-form questions are treated as untrusted data in the user segment; the OpenAI provider auto-configurations are excluded so boot requires no API key; production boot with `enabled=false` is byte-for-byte the Phase 5 application.

---

## Ownership Summary (Phase 2-5)

| Domain | Owns | Does NOT Own |
|--------|------|--------------|
| Enrichment | Context building | Feature/ML/Decision |
| Feature | Computation + snapshot | ML inference |
| ML (Python) | Model, inference, factors | Policy, decision, evidence |
| Rule | Explicit findings | Policy, ML score |
| Policy | Business action | Model, rules |
| Evidence | Explanation/traceability | Decision |
| Pipeline | Orchestration | Domain logic |
| Analytics (P4) | Replay, simulation, counterfactual artifacts | Production decisions, policy, mutation |
| Investigation App (P4) | Read-only analytical workflow | Transaction/decision mutation |
| Frontend (P5) | Presentation contract | Any risk/decision/policy logic |
| AI Investigator (P6) | Evidence-grounded explanation + audit trail | Decisions, policy, scoring, mutation |

**Critical Separations:**
- ML does not own final decisions
- Rule engine does not own policy
- Policy does not own model inference
- Evidence does not determine decisions
- Pipeline does not contain domain logic

---

## Enforcement
- Package-private / module-private where possible
- No `@Entity` references across modules in JPA (use IDs + repository lookups)
- Service layer enforces boundaries via `@Transactional` service methods (but pipeline avoids holding txn during ML call)
- Database FKs enforce referential integrity at storage layer

---

## Phase 3 Extensions

| Phase | New Module | Integrates With | Status |
|-------|------------|-----------------|--------|
| 3 | `kafka` | Pipeline (transport, not logic) | **implemented** |

## Phase 4 Extensions

| Phase | New Module | Integrates With | Status |
|-------|------------|-----------------|--------|
| 4 | `analytics.replay` (incl. evidence graph collector + `DisagreementInfo`) | Decision, Risk, Evidence, Transaction | **implemented** |
| 4 | `analytics.policy` (Policy Lab) | Decision (DecisionRecord), Risk (FeatureSnapshot/RiskScore) | **implemented** |
| 4 | `analytics.counterfactual` | Risk (FeatureSnapshot/RiskScore), Decision, ML client | **implemented** |
| 4 | `investigation.service` (application facade) | Investigation, all analytics | **implemented** |
| 4 | `policy-lab` UI / `investigation-ui` UI (Phase 3 doc) | — | **built as `frontend/` in Phase 5** |

## Phase 5 Extensions

| Phase | New Module | Integrates With | Status |
|-------|------------|-----------------|--------|
| 5 | `frontend` (React/Vite/TS) | All public `/api` controllers, dev proxy | **implemented** |
| 5 | `TransactionOverview` (`com.sentinelflow.transaction.dto`) | Transaction | **implemented** |
| 5 | `TransactionProcessController` (`com.sentinelflow.api`) | Pipeline (thin facade) | **implemented** |
| 5 | `CounterfactualController.features` | CounterfactualFeatureRegistry | **implemented** |

## Phase 6 Extensions

| Phase | New Module | Integrates With | Status |
|-------|------------|-----------------|--------|
| 6 | `ai` (prompt/gateway/tool/validation/service) | Investigation app context, evidence graph, decision replay, policy lab, counterfactuals (read-only) | **implemented** |
| 6 | `ai_investigation_runs` (Flyway V12) | Investigations (ON DELETE CASCADE) | **implemented** |
| 6 | `AiInvestigationController` (`com.sentinelflow.api`) | `AiInvestigationService` | **implemented** |
| 6 | `AiInvestigationPanel` (`frontend/src/components/ai`) | `investigationsApi.explain` / `explanationRuns` | **implemented** |

Each new module follows the same pattern: own entities, repositories, service layer, clear boundaries.