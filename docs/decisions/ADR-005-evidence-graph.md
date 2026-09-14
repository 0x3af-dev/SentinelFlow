# ADR-005: Evidence Graph in PostgreSQL

## Context
SentinelFlow's investigation UI and audit trail need to show the evidence chain: Transaction → Device/Location → Features → Model Prediction → Risk Factors → Decision. This is a graph structure.

## Decision
Model the evidence graph as **two relational tables in PostgreSQL**:
- `evidence_nodes` — vertices with typed nodes (TRANSACTION, DEVICE, MODEL_PREDICTION, etc.)
- `evidence_edges` — directed, typed edges (USED_DEVICE, CONTRIBUTES_TO, etc.)

Both use controlled enums via CHECK constraints for node_type, source_type, relationship_type.

## Alternatives Considered
1. **Neo4j** — Native graph database
   - Pros: Cypher query language, native traversal, ACID
   - Cons: Additional infrastructure, operational complexity, data sync with PostgreSQL, overkill for Phase 1 graph size
2. **PostgreSQL recursive CTEs (chosen)** — Model graph in relational tables
   - Pros: Single database, ACID across domains, Flyway-managed, Spring Data JPA repositories, recursive CTEs for traversal, JSONB for flexible payloads
   - Cons: Traversal via recursive CTE is slower than native graph for deep paths; no Cypher
3. **Adjacency list in JSONB** — Store edges as JSON in node
   - Pros: Single table
   - Cons: Hard to query reverse edges, no FK enforcement, no edge metadata

## Reasoning
Phase 1 evidence graph:
- ~10-20 nodes per transaction
- Max depth ~5 (Transaction → Device → Behavior → Factor → Decision)
- Traversal patterns: "outgoing from transaction", "incoming to decision", "full chain"
- Recursive CTEs handle this easily in PostgreSQL
- Single database = single transaction for decision + evidence creation
- No polyglot persistence until proven necessary

## Tradeoffs
- Accept: Recursive CTE traversal for deep paths (mitigated by indexes on source_node_id, target_node_id)
- Accept: No Cypher — traversal via SQL or application-layer iteration
- Gain: Zero additional infrastructure, full ACID, unified migrations, JPA repositories

## Consequences
- `evidence_nodes` has polymorphic `entity_type` + `entity_id` for domain object references
- `evidence_edges` has UNIQUE constraint on (source, target, relationship) to prevent duplicates
- CHECK constraints enforce controlled vocabularies (extensible via migration)
- Spring Data repositories: `findBySourceNodeId`, `findByTargetNodeId`, `findByEntityTypeAndEntityId`
- Future: If traversal performance becomes bottleneck, add materialized paths or extract to Neo4j

## Status
Accepted — implemented in Phase 1 (V6 migration)