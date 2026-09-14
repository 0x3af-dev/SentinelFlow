# Rule Engine

## Purpose
Deterministic explicit condition checks, separate from ML.

ML asks: *How risky does model think this is?*
Rules ask: *Did an explicit known condition occur?*

## Interface
```java
Rule { String getRuleId(); String getVersion(); RuleResult evaluate(EnrichmentContext, FeatureSet) }
RuleEngine { RuleEvaluationResult evaluate(context, features) } // runs all registered rules
```

## Rules (v1)

| ID | Name | Trigger | Severity | Version |
|----|------|---------|----------|---------|
| RULE-001 | HighAmount | transaction_amount ≥ 50000 | HIGH | v1 |
| RULE-002 | NewDevice | is_new_device==1 | HIGH | v1 |
| RULE-003 | HighVelocity | transactions_last_1_hour ≥10 | HIGH | v1 |
| RULE-004 | NewLocation | is_new_location==1 | HIGH | v1 |
| RULE-005 | CombinedSuspicion | ≥3 independent signals (new_device, new_location, velocity≥5, amount≥10000, account_age<30) | HIGH | v1 |

## Output
```java
RuleResult(ruleId, ruleVersion, triggered, severity, description, observedValues)
```
Example:
```json
{"rule_id":"RULE-001","rule_version":"v1","triggered":true,"severity":"HIGH","description":"Transaction amount exceeds high-value threshold","observed_values":{"transaction_amount":60000,"threshold":50000}}
```

## Versioning
- Each rule has `rule_id + version`
- Threshold change → new version (RULE-001 v1 vs v2 distinguishable)
- Historical decisions preserve rule version

## Determinism
- Same `transaction + feature snapshot + rule version` → same output
- No LLM, no random

## Tests
- Each rule: trigger, non-trigger, boundary (threshold-ε, threshold, threshold+ε), missing data
- Engine: aggregates triggered count

## Not in Phase 2
- No Drools, no DSL, no visual builder; straightforward Java logic behind interface for future replacement
