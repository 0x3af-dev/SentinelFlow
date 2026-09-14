# ADR-009 — Python ML Service Boundary

**Status:** Accepted

**Context:** Java app should not embed ML model; model training/serving needs Python ecosystem (scikit-learn) but business policy must remain in Java.

**Decision:** Separate Python FastAPI service (`ml-risk-service`) owns model loading, feature validation, inference, metadata. Java `MlInferenceClient` via HTTP/REST, validated for model_name, feature_schema, score 0..1, timeout 5s. ML answers "How risky?" not "Should we block?".

**Consequences:** Clear separation, independent deployment, lineage via model_version, testable via mock, no distributed transaction (DB txn ≠ ML call).
