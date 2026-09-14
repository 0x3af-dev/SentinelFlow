# ADR-010 — Feature Snapshot Versioning

**Status:** Accepted

**Context:** Feature logic will evolve; historical decisions must remain reproducible.

**Decision:** Feature schema version `fs-v1` persisted in `FeatureSnapshot.feature_schema_version` and `ModelVersion.feature_schema_version`. Snapshots immutable (new row on rescoring). Model rejects incompatible schema.

**Consequences:** Historical replay possible via transaction + snapshot + model + policy; no silent drift.
