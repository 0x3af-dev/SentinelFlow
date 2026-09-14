# ADR-006: JSONB Usage Policy

## Context
PostgreSQL's JSONB provides flexible document storage. SentinelFlow must decide where JSONB is appropriate vs. where relational columns are required.

## Decision
**Use JSONB only for genuinely flexible/evolving structures.** Core business data remains relational.

### Approved JSONB Uses
| Table | Column | Rationale |
|-------|--------|-----------|
| `feature_snapshots` | `features` | Feature vectors evolve per model version; schema not fixed |
| `decision_policies` | `configuration` | Policy rules/thresholds vary; extensible without migration |
| `evidence_nodes` | `value`, `metadata` | Evidence payloads vary by node_type; flexible attributes |
| `evidence_edges` | `metadata` | Edge annotations vary by relationship_type |
| `investigation_events` | `payload` | Event details vary by event_type |
| `audit_logs` | `previous_state`, `new_state`, `metadata` | Captures arbitrary entity state changes |

### Explicitly NOT JSONB
| Data | Reason |
|------|--------|
| Transaction amount, currency | Financial precision, querying, indexing |
| Transaction timestamp | Temporal queries, partitioning |
| Transaction status | Lifecycle state machine, indexing |
| Risk score, prediction | Numerical range queries, ordering |
| Model version, policy version | Foreign keys, version lineage |
| Investigation status, resolution | State machine, reporting |
| User/device/location core attributes | Relational integrity, FK targets |

## Alternatives Considered
1. **All JSONB** — Schema-less flexibility
   - Pros: No migrations for new fields
   - Cons: No constraints, poor query performance, no FK, data quality issues
2. **All relational** — Strict schema
   - Pros: Maximum integrity
   - Cons: Migration overhead for genuinely flexible data (features, policy config)
3. **Hybrid (chosen)** — Relational core, JSONB periphery

## Reasoning
The hybrid approach matches the domain:
- Core domain (transactions, decisions, investigations) is stable → relational
- ML features, policy config, evidence metadata evolve independently → JSONB
- JSONB in PostgreSQL supports GIN indexes for query performance where needed
- Spring Data JPA maps JSONB to `Map<String, Object>` with `@JdbcTypeCode(SqlTypes.JSON)`

## Tradeoffs
- Accept: Two patterns to understand
- Accept: JSONB columns need application-level validation (no DB CHECK on structure)
- Gain: Schema stability for core, flexibility for evolving data

## Consequences
- Migrations add JSONB columns with `columnDefinition = "jsonb"`
- JPA entities use `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`
- No JSONB for primary keys, foreign keys, or indexed search predicates on core fields
- Future: GIN indexes on JSONB paths if query patterns demand (e.g., `features->>'velocity_1h'`)

## Status
Accepted — implemented in Phase 1