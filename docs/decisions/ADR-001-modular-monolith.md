# ADR-001: Modular Monolith Architecture

## Context
SentinelFlow is a risk decision and investigation platform that will eventually include transaction processing, ML inference, rule evaluation, policy engine, evidence graph, investigation UI, and more.

## Decision
Implement SentinelFlow as a **modular monolith** — a single Spring Boot application with internal module boundaries (packages) for:
- `identity` (users, devices, locations)
- `transaction` (merchants, transactions)
- `risk` (model versions, features, scores, factors)
- `decision` (policies, decisions)
- `evidence` (nodes, edges)
- `investigation` (investigations, events, audit)
- `shared` (base entity, common utilities)

Each module owns its entities, repositories, and domain logic. Cross-module references use service-layer calls, not direct entity access where possible.

## Alternatives Considered
1. **Microservices** — Separate deployable services per domain.
   - Pros: Independent scaling, team autonomy, technology diversity
   - Cons: Operational complexity, distributed transactions, network latency, eventual consistency, harder debugging, premature for Phase 1
2. **Modular Monolith (chosen)** — Single deployment, clear internal boundaries.
   - Pros: Simple deployment, ACID transactions, easy refactoring, clear module boundaries, can extract services later if needed
   - Cons: Single point of failure, shared database, scaling is all-or-nothing

## Reasoning
Phase 1 is the persistence foundation. The domain model is cohesive and transactional (decisions must reference transactions, risk scores, policies atomically). Microservices would introduce distributed system complexity before the domain is stable. The modular monolith preserves the option to extract services in Phase 3+ when boundaries are proven.

## Tradeoffs
- Accept: Single database, single deployment unit
- Accept: All modules scale together
- Gain: Transactional integrity across domains, simpler operations, faster development

## Consequences
- Shared PostgreSQL database for all modules
- Flyway migrations are linear (not per-module)
- JPA entities can reference across modules via FK (enforced at DB level)
- Internal module communication via Spring beans, not REST
- Future service extraction requires: separate databases, API contracts, saga/choreography for cross-domain operations

## Status
Accepted — implemented in Phase 1