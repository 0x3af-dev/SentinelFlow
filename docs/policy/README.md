# Policy Evaluator

## Policy
- **Name:** `fraud-policy`, **Version:** `v1`, **Status:** ACTIVE
- **Configuration (JSONB):** `{"review_threshold": 0.5, "block_threshold": 0.85}`
- Persisted in `decision_policies` (immutable version)

## Semantics
```
risk_score < 0.50 → ALLOW
0.50 ≤ risk_score < 0.85 → REVIEW
risk_score ≥ 0.85 → BLOCK
```
- Reads thresholds from persisted policy, does NOT hardcode
- Validates: 0≤review≤1, 0≤block≤1, review<block; fails safely on invalid (no arbitrary decision)

## Interface
```java
PolicyEvaluator { PolicyEvaluationResult evaluate(RiskScore, RuleEvaluationResult) }
```
Returns `decision, reason, policyReference` with deterministic reason:
```
Policy fraud-policy/v1 evaluated risk score 0.72, which falls within the review threshold range.
```

## Rule + ML Interaction
- Input: `ML risk_score + rule findings`
- Phase 2: score-driven, but preserves rule findings for future policy versions
- Rule engine never modifies ML score

## Disagreement Detection
- Preserves `ML score vs rule findings` disagreement as diagnostic (detect, record, explain) without altering score
- Future: may become REVIEW via policy, but Phase 2 only records

## Tests
- Threshold boundaries: 0.00,0.49,0.50,0.51,0.84,0.85,0.86,1.00
- Validation: review=-0.1,1.2, block=-0.1,1.2, review≥block all fail
