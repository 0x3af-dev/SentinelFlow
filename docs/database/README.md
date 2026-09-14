# SentinelFlow Database Documentation

## Overview

This document describes the database schema for SentinelFlow Phase 1 — the persistence foundation for an event-driven risk decision and investigation platform.

**Technology:** PostgreSQL 16, Flyway migrations, Spring Data JPA (Hibernate)

**Naming Conventions:**
- Tables: lowercase snake_case, plural
- Primary Keys: UUID (`id DEFAULT gen_random_uuid()`)
- Timestamps: `TIMESTAMPTZ` stored in UTC
- Money: `NUMERIC(19,4)`, never floating-point
- Flexible payloads: JSONB only for features, policy config, evidence metadata, event payload

## Tables

### 1. users
**Purpose:** Synthetic user identities (no passwords/secrets stored).

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK, DEFAULT gen_random_uuid() |
| external_reference | VARCHAR(64) | NOT NULL, UNIQUE |
| display_name | VARCHAR(255) | NOT NULL |
| contact_reference | VARCHAR(255) | NULL |
| status | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE', CHECK (ACTIVE, SUSPENDED, CLOSED) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_users_status (status)`

---

### 2. devices
**Purpose:** User devices for behavioral analysis.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | NOT NULL, FK → users(id) |
| device_reference | VARCHAR(64) | NOT NULL, UNIQUE |
| device_type | VARCHAR(64) | NULL |
| platform | VARCHAR(64) | NULL |
| first_seen_at | TIMESTAMPTZ | NULL |
| last_seen_at | TIMESTAMPTZ | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_devices_user_id (user_id)`

---

### 3. locations
**Purpose:** User locations for geographic risk analysis.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | NOT NULL, FK → users(id) |
| country | VARCHAR(2) | NULL |
| region | VARCHAR(128) | NULL |
| city | VARCHAR(128) | NULL |
| latitude | DOUBLE PRECISION | NULL |
| longitude | DOUBLE PRECISION | NULL |
| first_seen_at | TIMESTAMPTZ | NULL |
| last_seen_at | TIMESTAMPTZ | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_locations_user_id (user_id)`

---

### 4. merchants
**Purpose:** Merchant entities for transaction categorization.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| external_reference | VARCHAR(64) | NOT NULL, UNIQUE |
| name | VARCHAR(255) | NOT NULL |
| category | VARCHAR(128) | NULL |
| country | VARCHAR(2) | NULL |
| status | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE', CHECK (ACTIVE, INACTIVE, SUSPENDED) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_merchants_status (status)`, `idx_merchants_category (category)`

---

### 5. transactions
**Purpose:** Central business entity — financial transactions.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| transaction_reference | VARCHAR(64) | NOT NULL, UNIQUE |
| user_id | UUID | NOT NULL, FK → users(id) |
| merchant_id | UUID | NOT NULL, FK → merchants(id) |
| device_id | UUID | FK → devices(id) |
| location_id | UUID | FK → locations(id) |
| amount | NUMERIC(19,4) | NOT NULL, CHECK (amount >= 0) |
| currency | VARCHAR(3) | NOT NULL |
| transaction_type | VARCHAR(32) | NULL |
| channel | VARCHAR(32) | NULL |
| transaction_timestamp | TIMESTAMPTZ | NOT NULL |
| status | VARCHAR(16) | NOT NULL DEFAULT 'RECEIVED', CHECK (RECEIVED, ENRICHING, SCORING, DECIDED, COMPLETED, FAILED) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_transactions_user_id`, `idx_transactions_merchant_id`, `idx_transactions_status`, `idx_transactions_timestamp`

> **Note:** `status` is processing lifecycle state, NOT fraud classification. Fraud decisions live in `decision_records`.

---

### 6. model_versions
**Purpose:** Immutable ML model versions for reproducibility/replay.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| model_name | VARCHAR(128) | NOT NULL |
| version | VARCHAR(32) | NOT NULL |
| algorithm | VARCHAR(128) | NULL |
| feature_schema_version | VARCHAR(32) | NULL |
| training_dataset_reference | VARCHAR(255) | NULL |
| artifact_reference | VARCHAR(255) | NULL |
| status | VARCHAR(16) | NOT NULL DEFAULT 'CANDIDATE', CHECK (TRAINING, EVALUATION, CANDIDATE, ACTIVE, RETIRED) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| activated_at | TIMESTAMPTZ | NULL |
| retired_at | TIMESTAMPTZ | NULL |

**Constraints:** UNIQUE (model_name, version)

---

### 7. feature_snapshots
**Purpose:** Immutable feature vectors per evaluation (for replay/counterfactuals).

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| transaction_id | UUID | NOT NULL, FK → transactions(id) |
| feature_schema_version | VARCHAR(32) | NULL |
| features | JSONB | NULL |
| generated_at | TIMESTAMPTZ | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_feature_snapshots_transaction_id (transaction_id)`

> **Rule:** Never overwrite historical snapshots. New calculation = new row.

---

### 8. risk_scores
**Purpose:** ML predictions linked to model version.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| transaction_id | UUID | NOT NULL, FK → transactions(id) |
| model_version_id | UUID | NOT NULL, FK → model_versions(id) |
| risk_score | DOUBLE PRECISION | NOT NULL, CHECK (0.0 <= risk_score <= 1.0) |
| prediction | VARCHAR(16) | NULL |
| inference_timestamp | TIMESTAMPTZ | NULL |
| inference_latency_ms | INTEGER | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_risk_scores_transaction_id`, `idx_risk_scores_model_version_id`

> **Rule:** Risk score ≠ final decision. Policy engine determines decision.

---

### 9. risk_factors
**Purpose:** Individual risk contributors (rule outputs, anomalies, model factors).

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| transaction_id | UUID | NOT NULL, FK → transactions(id) |
| factor_type | VARCHAR(64) | NOT NULL |
| description | TEXT | NULL |
| severity | VARCHAR(16) | NULL |
| source | VARCHAR(64) | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_risk_factors_transaction_id`, `idx_risk_factors_factor_type`

> Factor types intentionally unconstrained to allow evolution.

---

### 10. decision_policies
**Purpose:** Immutable decision policy versions (thresholds, rules config).

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| policy_name | VARCHAR(128) | NOT NULL |
| version | VARCHAR(32) | NOT NULL |
| description | TEXT | NULL |
| configuration | JSONB | NULL |
| status | VARCHAR(16) | NOT NULL DEFAULT 'DRAFT', CHECK (DRAFT, ACTIVE, RETIRED) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| activated_at | TIMESTAMPTZ | NULL |
| retired_at | TIMESTAMPTZ | NULL |

**Constraints:** UNIQUE (policy_name, version)

---

### 11. decision_records
**Purpose:** Final risk decisions with full lineage.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| transaction_id | UUID | NOT NULL, FK → transactions(id) |
| risk_score_id | UUID | FK → risk_scores(id) |
| policy_id | UUID | FK → decision_policies(id) |
| final_decision | VARCHAR(16) | NOT NULL, CHECK (ALLOW, REVIEW, BLOCK) |
| decision_timestamp | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| decision_reason | TEXT | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_decision_records_transaction_id`, `idx_decision_records_decision`, `idx_decision_records_timestamp`

> Lineage: Transaction → FeatureSnapshot → RiskScore → ModelVersion → DecisionPolicy → DecisionRecord

---

### 12. evidence_nodes
**Purpose:** Evidence graph nodes — artifacts in the decision chain.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| node_type | VARCHAR(32) | NOT NULL, CHECK (controlled enum) |
| source_type | VARCHAR(32) | NOT NULL, CHECK (controlled enum) |
| entity_type | VARCHAR(32) | NULL |
| entity_id | UUID | NULL |
| observed_at | TIMESTAMPTZ | NULL |
| value | JSONB | NULL |
| confidence | DOUBLE PRECISION | NULL |
| metadata | JSONB | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_evidence_nodes_entity (entity_type, entity_id)`, `idx_evidence_nodes_type (node_type)`, `idx_evidence_nodes_observed_at (observed_at)`

**Node Types:** TRANSACTION, USER, DEVICE, LOCATION, MERCHANT, BEHAVIOR, FEATURE, MODEL_PREDICTION, RISK_FACTOR, RULE_RESULT, POLICY, DECISION, INVESTIGATION, EVENT

**Source Types:** TRANSACTION_RECORD, USER_ACTIVITY, DEVICE_HISTORY, LOCATION_HISTORY, MODEL_OUTPUT, RULE_ENGINE, POLICY_ENGINE, EXTERNAL_PROVIDER, INVESTIGATOR

---

### 13. evidence_edges
**Purpose:** Evidence graph edges — typed relationships between nodes.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| source_node_id | UUID | NOT NULL, FK → evidence_nodes(id) |
| target_node_id | UUID | NOT NULL, FK → evidence_nodes(id) |
| relationship_type | VARCHAR(32) | NOT NULL, CHECK (controlled enum) |
| metadata | JSONB | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Constraints:** UNIQUE (source_node_id, target_node_id, relationship_type)

**Indexes:** `idx_evidence_edges_source (source_node_id)`, `idx_evidence_edges_target (target_node_id)`

**Relationship Types:** PERFORMED_BY, USED_DEVICE, OCCURRED_AT, GENERATED_FEATURE, USED_BY_MODEL, PRODUCED, CONTRIBUTES_TO, GOVERNED_BY, ASSOCIATED_WITH, SUPPORTS, CONTRADICTS, REFERENCES

> Edge direction: source → target (e.g., Transaction USED_DEVICE → Device)

---

### 14. investigations
**Purpose:** Human investigation cases.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| investigation_reference | VARCHAR(64) | NOT NULL, UNIQUE |
| transaction_id | UUID | NOT NULL, FK → transactions(id) |
| status | VARCHAR(16) | NOT NULL DEFAULT 'OPEN', CHECK (OPEN, INVESTIGATING, RESOLVED) |
| priority | VARCHAR(16) | DEFAULT 'MEDIUM', CHECK (LOW, MEDIUM, HIGH, CRITICAL) |
| assigned_to | VARCHAR(128) | NULL |
| opened_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| resolved_at | TIMESTAMPTZ | NULL |
| resolution | VARCHAR(32) | CHECK (CONFIRMED_FRAUD, FALSE_POSITIVE, INCONCLUSIVE) |
| resolution_notes | TEXT | NULL |

**Indexes:** `idx_investigations_transaction_id`, `idx_investigations_status`, `idx_investigations_assigned_to`

> **Rule:** `status` ≠ `resolution`. An investigation can be RESOLVED with FALSE_POSITIVE or CONFIRMED_FRAUD.

---

### 15. investigation_events
**Purpose:** Append-only investigation history (never overwritten).

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| investigation_id | UUID | NOT NULL, FK → investigations(id) |
| event_type | VARCHAR(64) | NOT NULL |
| actor_type | VARCHAR(32) | NOT NULL |
| actor_reference | VARCHAR(128) | NULL |
| event_timestamp | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| payload | JSONB | NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

**Indexes:** `idx_investigation_events_investigation_id`, `idx_investigation_events_timestamp`

**Event Types:** INVESTIGATION_CREATED, EVIDENCE_ADDED, STATUS_CHANGED, ANALYST_NOTE_ADDED, AI_ANALYSIS_COMPLETED, RESOLUTION_RECORDED

---

### 16. audit_logs
**Purpose:** Generic entity-level audit trail.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| actor_type | VARCHAR(32) | NOT NULL |
| actor_reference | VARCHAR(128) | NULL |
| action | VARCHAR(64) | NOT NULL |
| entity_type | VARCHAR(64) | NOT NULL |
| entity_id | UUID | NULL |
| previous_state | JSONB | NULL |
| new_state | JSONB | NULL |
| timestamp | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| metadata | JSONB | NULL |
| correlation_reference | VARCHAR(64) | NULL |

**Indexes:** `idx_audit_logs_entity (entity_type, entity_id)`, `idx_audit_logs_timestamp`, `idx_audit_logs_actor (actor_type, actor_reference)`, `idx_audit_logs_correlation (correlation_reference)`

> Distinct from: business events (Kafka, later phase), investigation events.

---

## Migration Order

| Version | Description |
|---------|-------------|
| V1 | Baseline (pgcrypto extension) |
| V2 | Identity (users, devices, locations) |
| V3 | Transaction (merchants, transactions) |
| V4 | Risk + Model (model_versions, feature_snapshots, risk_scores, risk_factors) |
| V5 | Decision + Policy (decision_policies, decision_records) |
| V6 | Evidence Graph (evidence_nodes, evidence_edges) |
| V7 | Investigation + Audit (investigations, investigation_events, audit_logs) |
| V8 | Deterministic Seed Data |

## Key Design Principles

1. **Historical Immutability:** Model versions, policy versions, feature snapshots, decisions, evidence, investigation events, and audit logs are never overwritten.
2. **Relational Core + JSONB Periphery:** Core business data is relational; flexible/configurable data uses JSONB.
3. **Decision Lineage:** Every decision traces to transaction → features → model → policy.
4. **Evidence Graph:** Persisted graph structure for investigation UI and audit.
5. **UTC Timestamps:** All timestamps stored in UTC via `TIMESTAMPTZ`.
6. **No Secrets:** No passwords, tokens, or credentials stored.