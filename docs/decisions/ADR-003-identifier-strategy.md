# ADR-003: Identifier Strategy — UUID

## Context
SentinelFlow entities need globally unique, non-sequential identifiers that work across distributed systems, support merge/replication, and don't leak business information.

## Decision
Use **UUID v4 (random)** as primary key for all entities.
- Generation: PostgreSQL `gen_random_uuid()` (via pgcrypto) as column default
- JPA: `@GeneratedValue(strategy = GenerationType.UUID)` / `@UuidGenerator`
- No business meaning in UUIDs — purely technical surrogate keys
- Business identifiers (e.g., `transaction_reference`, `external_reference`) are separate columns with UNIQUE constraints

## Alternatives Considered
1. **Auto-increment BIGINT** — Simple, small
   - Pros: 8 bytes, sequential, fast inserts
   - Cons: Leaks row counts, problematic for sharding/merge, predictable
2. **UUID v1 (time-based)** — Ordered, unique
   - Pros: Sortable by creation time
   - Cons: Exposes MAC/timestamp, predictable
3. **UUID v4 random (chosen)** — 128-bit random
   - Pros: No information leakage, globally unique, works offline, standard
   - Cons: 16 bytes (larger indexes), not naturally ordered (mitigated by `created_at`)
4. **ULID / KSUID** — Sortable, compact
   - Pros: Time-ordered, 16-20 bytes
   - Cons: Less standard, extra dependency

## Reasoning
- UUID v4 is the industry standard for distributed systems
- PostgreSQL has native `gen_random_uuid()` (no app-side generation needed)
- Spring Data JPA / Hibernate 6 have first-class UUID support
- `created_at` provides ordering where needed
- 16-byte index size is acceptable for Phase 1 scale

## Tradeoffs
- Accept: Larger indexes (16 bytes vs 8 bytes for BIGINT)
- Accept: Not human-readable (use business references for debugging)
- Gain: No coordination needed, works in offline/edge scenarios, secure

## Consequences
- All tables: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- JPA entities: `@Id @GeneratedValue(strategy = GenerationType.UUID)`
- Foreign keys reference UUID columns
- Business lookups use `*_reference` columns (indexed)
- API DTOs (Phase 2) will expose UUIDs or business references

## Status
Accepted — implemented in Phase 1