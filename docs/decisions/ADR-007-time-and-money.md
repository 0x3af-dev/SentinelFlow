# ADR-007: Time and Money Representation

## Context
Consistent representation of timestamps and monetary values is critical for financial risk systems.

## Decision

### Timestamps
- **Storage:** `TIMESTAMPTZ` (timestamp with time zone) in PostgreSQL
- **Application:** `java.time.Instant` (UTC-based, timezone-aware)
- **Timezone:** All timestamps stored and processed in **UTC**
- **Database default:** `NOW()` returns UTC in PostgreSQL when server timezone is UTC
- **Application config:** `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` (implicit via Instant)
- **No reliance** on server/local timezone

**Columns:** `created_at`, `updated_at`, `transaction_timestamp`, `observed_at`, `inference_timestamp`, `decision_timestamp`, `event_timestamp`, `opened_at`, `resolved_at`, `activated_at`, `retired_at`, `first_seen_at`, `last_seen_at`

### Money
- **Storage:** `NUMERIC(19,4)` — fixed-point decimal, 15 digits integer + 4 decimal places
- **Application:** `java.math.BigDecimal`
- **Currency:** Explicit `VARCHAR(3)` ISO 4217 code column (e.g., `INR`, `USD`) on every monetary table
- **Constraints:** `CHECK (amount >= 0)` on transaction amounts
- **No floating-point** (DOUBLE PRECISION, FLOAT, REAL) for money

**Columns:** `transactions.amount` + `transactions.currency`

## Alternatives Considered
1. **TIMESTAMP WITHOUT TIME ZONE** — Simpler
   - Pros: Slightly smaller
   - Cons: Ambiguous, requires app to track timezone, DST bugs
2. **BIGINT epoch millis** — Timezone-neutral
   - Pros: Simple integer
   - Cons: Not human-readable in DB, requires conversion, loses timezone context
3. **TIMESTAMPTZ + Instant (chosen)** — Standard, correct
4. **NUMERIC(19,2)** — 2 decimal places
   - Pros: Common for USD
   - Cons: INR and other currencies use 2-4 decimals; crypto needs more
5. **NUMERIC(19,4) (chosen)** — Supports all major currencies + sub-unit precision
6. **Integer minor units (paise/cents)** — No decimals
   - Pros: Exact integer arithmetic
   - Cons: Display logic complexity, inconsistent across currencies

## Reasoning
- `TIMESTAMPTZ` + `Instant` is the only correct way to handle timestamps across timezones
- `NUMERIC(19,4)` accommodates INR (2 decimals), USD (2), JPY (0), BTC (8 via 4+4), and intermediate calculations
- Explicit currency column prevents "assume USD" bugs
- Spring Boot 3 / Hibernate 6 map these types correctly

## Tradeoffs
- Accept: `NUMERIC(19,4)` uses more storage than integer minor units
- Accept: Must always specify currency (no default)
- Gain: Correctness, auditability, multi-currency ready

## Consequences
- All DDL uses `TIMESTAMPTZ` and `NUMERIC(19,4)`
- JPA entities: `Instant` for timestamps, `BigDecimal` for amounts
- Application never calls `.toLocalDateTime()` without timezone
- Seed data uses ISO timestamps with `+00` offset
- Flyway migrations use `TIMESTAMPTZ` and `NUMERIC(19,4)`

## Status
Accepted — implemented in Phase 1