# SentinelFlow Entity-Relationship Diagram

## Overview

This diagram shows the core entities and relationships for SentinelFlow Phase 1.

```
┌─────────────┐       ┌─────────────┐       ┌──────────────────┐
│   users     │       │  devices    │       │   locations      │
├─────────────┤       ├─────────────┤       ├──────────────────┤
│ id (PK)     │◄──────│ id (PK)     │       │ id (PK)          │
│ ext_ref     │ 1:N   │ user_id (FK)│       │ user_id (FK)     │
│ display_name│       │ dev_ref     │       │ country/region   │
│ status      │       │ type/platf. │       │ lat/long         │
└─────────────┘       └─────────────┘       └──────────────────┘
       │                     │                     │
       │                     │                     │
       ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      transactions                               │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)                  transaction_timestamp                  │
│ txn_ref (UNIQUE)         status (RECEIVED..COMPLETED)          │
│ user_id (FK)             amount (NUMERIC 19,4)                  │
│ merchant_id (FK)         currency (VARCHAR 3)                   │
│ device_id (FK)           type / channel                         │
│ location_id (FK)         created_at / updated_at                │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 1:N
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      merchants                                  │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)                                                         │
│ ext_ref (UNIQUE)                                                │
│ name / category / country                                       │
│ status (ACTIVE/INACTIVE/SUSPENDED)                              │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      model_versions                             │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              training_dataset_ref                       │
│ model_name           artifact_reference                         │
│ version (UNIQUE w/ name)                                        │
│ algorithm / schema_ver                                           │
│ status (TRAINING..RETIRED)                                       │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 1:N
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      feature_snapshots                          │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              features (JSONB)                           │
│ txn_id (FK)          generated_at                               │
│ schema_version       created_at                                 │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 1:1 (per evaluation, but N snapshots per txn)
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      risk_scores                                │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              risk_score (0.0-1.0)                       │
│ txn_id (FK)          prediction (HIGH/MEDIUM/LOW)               │
│ model_id (FK)        inference_timestamp / latency_ms           │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 1:N
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      risk_factors                               │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              factor_type (NEW_DEVICE, etc.)             │
│ txn_id (FK)          description / severity / source            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      decision_policies                          │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              configuration (JSONB)                      │
│ policy_name          status (DRAFT/ACTIVE/RETIRED)              │
│ version (UNIQUE w/ name)                                        │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 1:N
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      decision_records                           │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              final_decision (ALLOW/REVIEW/BLOCK)        │
│ txn_id (FK)          decision_timestamp                         │
│ risk_score_id (FK)   decision_reason                            │
│ policy_id (FK)       created_at                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      evidence_nodes                             │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              node_type (TRANSACTION, DEVICE, ...)       │
│ source_type          entity_type / entity_id (polymorphic)      │
│ observed_at          value (JSONB) / confidence / metadata      │
└─────────────────────────────────────────────────────────────────┘
       │ 1:N                          1:N
       ▼                              ▼
┌──────────────────┐          ┌──────────────────┐
│ evidence_edges   │          │ evidence_edges   │
├──────────────────┤          ├──────────────────┤
│ id (PK)          │          │ id (PK)          │
│ source_id (FK)   │          │ target_id (FK)   │
│ target_id (FK)   │          │ relationship     │
│ relationship     │          │ (PERFORMED_BY,   │
│                  │          │  USED_DEVICE,    │
└──────────────────┘          │  CONTRIBUTES_TO) │
                              └──────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      investigations                             │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              status (OPEN/INVESTIGATING/RESOLVED)       │
│ inv_ref (UNIQUE)     priority (LOW..CRITICAL)                   │
│ txn_id (FK)          assigned_to                                │
│ opened_at            resolution (CONFIRMED_FRAUD/               │
│ resolved_at          FALSE_POSITIVE/INCONCLUSIVE)               │
│ resolution_notes     updated_at                                 │
└─────────────────────────────────────────────────────────────────┘
       │ 1:N
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      investigation_events                       │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              event_type (STATUS_CHANGED, ...)           │
│ inv_id (FK)          actor_type / actor_reference               │
│ event_timestamp      payload (JSONB)                            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      audit_logs                                 │
├─────────────────────────────────────────────────────────────────┤
│ id (PK)              entity_type / entity_id                    │
│ actor_type           previous_state / new_state (JSONB)         │
│ actor_ref            timestamp / metadata / corr_ref            │
│ action                                                           │
└─────────────────────────────────────────────────────────────────┘
```

## Relationship Cardinalities

| From | To | Cardinality | Notes |
|------|-----|-------------|-------|
| users | devices | 1:N | User has many devices |
| users | locations | 1:N | User has many locations |
| users | transactions | 1:N | User makes many transactions |
| merchants | transactions | 1:N | Merchant receives many transactions |
| devices | transactions | 1:N (nullable) | Transaction may have device |
| locations | transactions | 1:N (nullable) | Transaction may have location |
| model_versions | risk_scores | 1:N | Model produces many scores |
| transactions | feature_snapshots | 1:N | Multiple snapshots per txn (history) |
| transactions | risk_scores | 1:N | Typically 1, but supports multiple models |
| transactions | risk_factors | 1:N | Many factors per transaction |
| transactions | decision_records | 1:N | Typically 1 final decision |
| risk_scores | decision_records | 1:1 (nullable) | Decision references score |
| decision_policies | decision_records | 1:N | Policy governs many decisions |
| evidence_nodes | evidence_edges | 1:N (source) | Outgoing edges |
| evidence_nodes | evidence_edges | 1:N (target) | Incoming edges |
| transactions | investigations | 1:N | Multiple investigations per txn possible |
| investigations | investigation_events | 1:N | Append-only event log |
| any entity | audit_logs | 1:N | Generic polymorphic audit |

## Evidence Graph Example (Persisted)

```
Transaction (TXN-SEED-COMBINED-001)
       │
       ├─ USED_DEVICE ─► Device (DEV-SEED-SUSPENDED)
       │
       ├─ GENERATED_FEATURE ─► Feature Snapshot (fs-v1)
       │                              │
       │                              └─ USED_BY_MODEL ─► Model Prediction (risk-model v1)
       │                                                         │
       │                                                         └─ PRODUCED ─► Risk Factor (HIGH_VELOCITY)
       │                                                                         │
       │                                                                         └─ CONTRIBUTES_TO ─► Decision (BLOCK)
       │
       └─ OCCURRED_AT ─► Location (Hyderabad)
```

All nodes and edges above are rows in `evidence_nodes` and `evidence_edges`.