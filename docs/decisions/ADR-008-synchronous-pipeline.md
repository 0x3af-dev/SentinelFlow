# ADR-008 — Synchronous Intelligence Pipeline

**Status:** Accepted

**Context:** Phase 2 needs to establish correct deterministic risk decision path before adding async transport. Premature Kafka would hide business logic errors and complicate testing.

**Decision:** Implement synchronous `TransactionIntelligencePipeline` orchestrating enrichment → features → ML → risk → rules → policy → decision → evidence. Business logic does NOT depend on Kafka; Kafka will later call the same pipeline.

**Consequences:** Simple to test, deterministic, no holding DB txn during ML call, later Kafka integration is just a transport wrapper.
