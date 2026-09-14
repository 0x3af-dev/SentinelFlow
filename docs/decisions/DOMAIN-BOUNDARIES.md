# SentinelFlow Phase 1 & 2 — Domain Boundaries

This document defines the ownership boundaries between modules in the modular monolith. Each module owns its entities, repositories, and domain logic. Cross-module access goes through service-layer APIs (added in Phase 2), not direct entity references.

**Phase 2 adds:** enrichment, feature, ml, rule, policy, pipeline, evidence service boundaries (see below).
**Phase 3 adds:** Kafka messaging/outbox boundary (§17) — transport only, no business logic.

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
- Policy simulation/lab (Phase 3)
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
- Investigation UI (Phase 2 React)
- Case assignment rules (Phase 3)
- Analyst workload management (Phase 3)
- Authentication/authorization (future security phase)

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
- Policy authoring UI (Phase 3 Policy Lab)
- Policy simulation (Phase 3)
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

---

## Ownership Summary (Phase 2-3)

| Domain | Owns | Does NOT Own |
|--------|------|--------------|
| Enrichment | Context building | Feature/ML/Decision |
| Feature | Computation + snapshot | ML inference |
| ML (Python) | Model, inference, factors | Policy, decision, evidence |
| Rule | Explicit findings | Policy, ML score |
| Policy | Business action | Model, rules |
| Evidence | Explanation/traceability | Decision |
| Pipeline | Orchestration | Domain logic |

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
| 3 | `policy-lab` | Decision (DecisionPolicy) | planned |
| 3 | `investigation-ui` | Investigation, Evidence | planned |
| 3 | `counterfactual` | Risk (FeatureSnapshot), Decision | planned |
| 3 | `simulation` | Decision, Risk, Transaction | planned |
| 3 | `kafka` | Pipeline (transport, not logic) | **implemented** |

Each new module follows the same pattern: own entities, repositories, service layer, clear boundaries.