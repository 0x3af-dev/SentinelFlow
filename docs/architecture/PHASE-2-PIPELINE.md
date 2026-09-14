# SentinelFlow Phase 2 — Transaction Intelligence Pipeline

## Overview
Phase 2 establishes the first deterministic end-to-end risk decision path:

```
Transaction → Enrichment → Feature Computation → FeatureSnapshot → ML Inference → RiskScore/RiskFactors → Rule Evaluation → Policy Evaluation → DecisionRecord → Evidence Graph
```

All stages are synchronous. No Kafka, Redis, or async processing in Phase 2. Services are designed to be callable via REST, Kafka consumer, replay engine, batch processor, and tests without duplicated business logic.

## Pipeline Stages

### 1. Transaction Enrichment (`enrichment.TransactionEnricher`)
- Input: `Transaction`
- Output: `EnrichmentContext` with:
  - UserBehaviorProfile (counts 24h/7d/30d, volumes, avg amount, first/last txn, known devices/locations)
  - DeviceProfile (isNewDevice, deviceTxnCount, deviceUserCount, first/last seen)
  - LocationProfile (isNewLocation, locationTxnCount, country/region/city)
  - MerchantProfile (merchantTxnCount, category, country)
- Read-only, distinguishes `known/unknown/not applicable`, handles `REQUIRED_CONTEXT_MISSING` vs `OPTIONAL_CONTEXT_MISSING`
- Excludes current transaction from historical counts to avoid self-inclusion bias

### 2. Feature Computation (`feature.FeatureComputationService`)
- Input: `EnrichmentContext`
- Output: `FeatureSet` (fs-v1) with ~23 numeric + 8 categorical features
- Features:
  - Transaction: amount, hour (UTC), day_of_week
  - User behavioral: counts/volumes 24h/7d/30d, avg amount, account_age_days
  - Velocity: transactions_last_10m/1h/24h
  - Device: is_new_device, device_txn_count, device_user_count, device_age_days
  - Location: is_new_location, location_txn_count, location_age_days
  - Merchant: merchant_txn_count, merchant_category_frequency, merchant_category/country, channel, transaction_type
- Deterministic given same transaction + historical data + schema version
- Uses injectable clock (Instant) where time affects velocity

### 3. Feature Snapshot Persistence (`risk.service.FeatureSnapshotService`)
- Persists immutable `FeatureSnapshot` (transaction_id, feature_schema_version, features JSONB, generated_at)
- Historical snapshots never overwritten; rescoring creates new row

### 4. ML Inference (`ml.MlInferenceClient` → Python `ml-risk-service`)
- Python service owns model loading, feature validation, inference, metadata
- Contract: request with all features, response with risk_score 0..1, prediction HIGH/MEDIUM/LOW, risk_factors, model_metadata, latency
- Validates model_name, feature_schema_version, score range
- Timeout 5000ms (configurable via `ml.client.timeout`), retries 2 with backoff
- Failure policy: no fabricated score (throws `MlInferenceException` → pipeline FAILED)

### 5. Risk Score Persistence (`risk.service.RiskScoringService`)
- Creates `RiskScore` (transaction, modelVersion, score, prediction, latency, timestamp) + `RiskFactor` per factor
- Immutable; historical scores retained
- ModelVersion auto-created if not exists (risk-model/v1, fs-v1)

### 6. Rule Engine (`rule.RuleEngine` + 5 rules)
- RULE-001 HighAmount (≥50000, HIGH)
- RULE-002 NewDevice (is_new_device==1, HIGH)
- RULE-003 HighVelocity (transactions_last_1_hour ≥10, HIGH)
- RULE-004 NewLocation (is_new_location==1, HIGH)
- RULE-005 CombinedSuspicion (≥3 independent signals, HIGH)
- Deterministic, versioned (v1), structured output (rule_id, version, triggered, severity, description, observed_values)

### 7. Policy Evaluator (`policy.PolicyEvaluator`)
- Loads persisted `fraud-policy/v1` (review 0.50, block 0.85) from DB, validates 0≤threshold≤1 and review<block
- Semantics: <0.5 ALLOW, 0.5–0.85 REVIEW, ≥0.85 BLOCK
- Returns structured `PolicyEvaluationResult` with reason and triggeredRuleIds

### 8. Decision Persistence (`decision.service.DecisionService`)
- Creates `DecisionRecord` (transaction, riskScore, policy, finalDecision, timestamp, reason)
- Immutable; idempotency via transaction+model+featureSchema+policy lineage (checks existing DecisionRecord by txn+riskScore+policy)

### 9. Evidence Generation (`evidence.service.EvidenceService`)
- Builds graph: TRANSACTION → USER, DEVICE, LOCATION, MERCHANT, FEATURE, MODEL, RISK_SCORE, RISK_FACTORS, RULE_RESULTS, POLICY, DECISION
- Nodes: TRANSACTION, USER, DEVICE, LOCATION, MERCHANT, FEATURE, MODEL_PREDICTION, RISK_FACTOR, RULE_RESULT, POLICY, DECISION
- Edges: PERFORMED_BY, USED_DEVICE, OCCURRED_AT, ASSOCIATED_WITH, GENERATED_FEATURE, USED_BY_MODEL, PRODUCED, CONTRIBUTES_TO, GOVERNED_BY
- Idempotent (checks existing nodes/edges before creating)

### 10. Orchestrator (`pipeline.TransactionIntelligencePipeline`)
- Orchestrates: load → enrich → features → snapshot → ML → risk → rules → policy → decision → evidence
- Returns `PipelineResult` with transactionReference, status, snapshotRef, modelVersion, riskScore, riskFactors, decision, policyVersion, timestamps
- Not @Transactional for whole pipeline (avoids holding DB txn during ML HTTP call); uses `saveAndFlush` for status updates
- Failure matrix: any stage failure → FAILED status, no fabricated data, no COMPLETED without decision

## Correlation
- transaction_reference is correlation ID throughout
- All nodes/edges/metrics logged with transaction_reference

## Historical Truth
- FeatureSnapshot, RiskScore, DecisionRecord, Evidence, ModelVersion, DecisionPolicy are immutable
- Rescoring creates new historical records, never overwrites

## Configuration
```yaml
ml.service.url: ${ML_SERVICE_URL:http://localhost:8001}
ml.client.timeout: ${ML_CLIENT_TIMEOUT:5000}
ml.expected.model-name: risk-model
ml.expected.feature-schema-version: fs-v1
```

## Transaction Status Lifecycle
```
RECEIVED → ENRICHING → SCORING → COMPLETED
   any stage → FAILED
```
