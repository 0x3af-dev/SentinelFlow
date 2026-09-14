# Pipeline Data Flow

```
Transaction
   ↓ (EnrichmentContext)
FeatureSet (fs-v1)
   ↓ (persist)
FeatureSnapshot
   ↓ (HTTP JSON)
MlPrediction (risk-model/v1, fs-v1, 0.0-1.0, factors, metadata)
   ↓ (persist)
RiskScore + RiskFactors
   ↓
RuleEvaluationResult (structured findings)
   ↓
PolicyEvaluationResult (ALLOW/REVIEW/BLOCK + reason)
   ↓ (persist)
DecisionRecord (lineage: txn → snapshot → model → policy)
   ↓
Evidence Graph (nodes + edges)
   ↓
PipelineResult
```

## Data Contracts

### EnrichmentContext
`Transaction, User, Device, Location, Merchant, UserBehaviorProfile, DeviceProfile, LocationProfile, MerchantProfile`

### FeatureSet
`featureSchemaVersion=fs-v1, generatedAt, features: Map<String,Object>`

### MlInferenceRequest (JSON)
23 numeric + 8 categorical features, same as FeatureSet

### MlInferenceResponse
`model_name, model_version, feature_schema_version, risk_score, prediction, risk_factors[], model_metadata, inference_latency_ms, inference_timestamp`

### RiskAssessment
`RiskScore + List<RiskFactor>`

### RuleEvaluationResult
`allResults[], triggeredRules[]` each with `rule_id, rule_version, triggered, severity, description, observed_values`

### PolicyEvaluationResult
`policyName, policyVersion, evaluatedAt, decision, reason, configuration, triggeredRuleIds`

### Decision
`transaction, riskScore, policy, finalDecision, decisionTimestamp, decisionReason`

### Evidence
Nodes polymorphic via `entity_type/entity_id`, edges via `source_node_id/target_node_id/relationship_type`

## Determinism
- Same `transaction + historical data + feature_schema_version` → same features
- Same `feature snapshot + rule version` → same rule findings
- Clock abstraction for velocity/time features (Instant.now() injected where needed, tests control time via fixed transaction_timestamp)

## Idempotency
Key: `transaction_id + model_version + feature_schema_version + policy_version`
- Reprocessing same txn with same versions → reuses existing DecisionRecord (checked via `findByTransactionIdAndRiskScoreIdAndPolicyId`)
- Evidence nodes/edges checked before creation
- Historical records retained on rescoring with new versions

## Failure Handling
| Stage | Failure | Behavior |
|-------|---------|----------|
| Transaction not found | 404 | fail cleanly, no status change |
| Enrichment missing optional | continue with flag (is_new_device=1 etc) |
| Enrichment required missing | FAILED |
| Feature computation | FAILED, no fabricated values |
| ML timeout/malformed | FAILED, no fabricated score |
| Rule engine | FAILED, no decision |
| Invalid policy | FAILED, no arbitrary decision |
| Evidence failure | WARN, pipeline still COMPLETED (decision is complete but evidence incomplete) |
