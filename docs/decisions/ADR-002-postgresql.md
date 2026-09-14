# ADR-002: PostgreSQL as Primary Data Store

## Context
SentinelFlow requires a reliable, transactional, relational database for Phase 1 persistence foundation with future extensibility for JSON documents, graph-like queries, and time-series data.

## Decision
Use **PostgreSQL 16** as the sole database for Phase 1.

## Alternatives Considered
1. **MySQL 8** — Mature, widely used
   - Pros: Familiar, good performance
   - Cons: Weaker JSONB support, no native UUID gen, weaker CHECK constraints, no partial indexes
2. **PostgreSQL (chosen)** — Advanced open-source RDBMS
   - Pros: Native UUID (`gen_random_uuid()`), JSONB with indexing (GIN), rich constraints, partial indexes, advisory locks, mature extension ecosystem (pgcrypto, pgvector later), strong Spring/Hibernate support
   - Cons: Slightly more complex operational tuning
3. **MongoDB** — Document store
   - Pros: Flexible schema
   - Cons: No ACID transactions across documents (until recently, still limited), no relational integrity, not suitable for financial/decision lineage
4. **Neo4j** — Graph database
   - Pros: Native graph traversal
   - Cons: Separate operational burden, not needed — evidence graph fits in PostgreSQL with recursive CTEs, introduces polyglot persistence prematurely

## Reasoning
PostgreSQL provides the best balance for Phase 1:
- Relational integrity for core domain (transactions, decisions, investigations)
- JSONB for flexible payloads (features, policy config, evidence metadata)
- Native UUID generation
- Mature Flyway + Spring Data JPA + Hibernate support
- Can model graph (evidence) with recursive queries; extract to Neo4j later if traversal performance demands it
- Single database simplifies transactions, backups, operations

## Tradeoffs
- Accept: Graph traversals use recursive CTEs (slower than native graph DB for deep traversals)
- Accept: Operational expertise needed for tuning
- Gain: Single source of truth, ACID across all domains, no data sync between stores

## Consequences
- All migrations target PostgreSQL
- JPA mappings use PostgreSQL-specific types (JSONB, UUID)
- Docker Compose uses `postgres:16-alpine`
- Testcontainers uses PostgreSQL for integration tests
- Future Phase 2+ may add Redis (caching), Kafka (events), but PostgreSQL remains system of record

## Status
Accepted — implemented in Phase 1