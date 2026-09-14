# ADR-021: Security and Governance (Phase 8)

**Status**: Accepted  
**Date**: 2026-09-14  
**Deciders**: Platform Team  
**Technical Story**: INV-1..18

---

## Context

SentinelFlow Phases 1–7 delivered a modular monolith for fraud investigation: transaction ingestion, enrichment, ML risk scoring, rule engine, policy evaluation, decision pipeline, evidence graph, analytics (replay, policy-lab, counterfactual), investigation workflow, AI investigator, and a React investigative workspace.

**Missing**: Authentication, authorization, audit governance, immutable data guarantees, and AI safety — required before any production deployment.

---

## Decision

Implement **Phase 8 — Security, Authorization, Data Protection & Governance** as a vertical slice adding:

1. **Stateless JWT Authentication** (HS256, JJWT 0.12.6)
   - Separate `security_users` table (not transaction-domain `users`)
   - BCrypt passwords, seeded demo users (analyst/investigator/operator/admin)
   - Short TTL (default 30 min), `NoOpServerSecurityContextRepository` (zero server sessions)
   - `POST /api/auth/login` → `{token, username, role, expiresInSeconds}`
   - `GET /api/auth/me` for session hydration
   - `POST /api/auth/logout` (client-side no-op)

2. **Role-Based Access Control (RBAC)**
   - Roles: `ANALYST`, `INVESTIGATOR`, `OPERATOR`, `ADMIN`, `SERVICE`
   - `SecurityConfig` path-matcher matrix (see SECURITY-ARCHITECTURE.md)
   - Blanket `/api/investigations/**` → `ANALYST/INVESTIGATOR/ADMIN` (blocks OPERATOR)
   - `/internal/**` → `SERVICE/ADMIN` + `X-Internal-Api-Key` header (`InternalApiKeyWebFilter`)

3. **Object-Level Authorization**
   - Investigation ownership: `assignedTo` from JWT principal
   - Service-layer check in `InvestigationApplicationService`
   - Actor derivation: all mutating endpoints ignore client-supplied `requestedBy`/`actorReference`

4. **Immutable Lineage Guarantees**
   - No UPDATE/DELETE endpoints on: `transactions`, `decision_records`, `risk_scores`, `feature_snapshots`, `evidence_nodes`, `evidence_edges`, `decision_policies`
   - Analytics append-only: `policy_simulations`, `counterfactual_analyses`
   - Investigation events append-only; resolution = status change
   - Audit log append-only; actor from JWT
   - Hash verification test (`ImmutableRecordsSecurityTest`)

5. **AI Security Hardening**
   - `PromptAssembler`: fixed system prompt + untrusted-data fence for free-form
   - `InvestigationAiTools`: exactly 4 read-only `@Tool` methods (reflection test)
   - `ExplanationValidator`: evidence-ID grounding, action-language rejection, hypothetical disclaimers
   - `AiInvestigationRun` audit: logged BEFORE service call (captures attempts even when AI frozen)

6. **Sensitive-Data Hygiene**
   - No secrets in logs; tokens never logged
   - No PII in AI prompts (synthetic IDs only)
   - Audit actor derived from JWT, never client
   - Secrets via env vars (`JWT_SECRET`, `INTERNAL_API_KEY`); test values in `application-test.yml` only

7. **Rate Limiting**
   - `LoginRateLimiter`: in-memory per-username, configurable max/window/cooldown
   - Lockout → 429 + `AUTHENTICATION_LOCKED_OUT` audit
   - Test override: 3 attempts / 600s / 600s

---

## Consequences

### Positive
- **Zero session state**: Horizontal scaling trivial; no Redis/session store
- **Clear boundaries**: Security domain (`com.sentinelflow.security`) owns only auth/user/audit/rate-limit — no transaction/domain logic
- **Testable**: 26 integration tests (shared Testcontainers PG), 3 unit tests, 3 HTTP tests updated to auth — 166 total backend tests pass
- **AI safety by default**: Disabled unless explicitly enabled; no provider bean without config; validation backstop
- **Audit completeness**: Every auth attempt, AI attempt, lockout recorded with JWT-derived actor

### Negative
- **In-memory rate limiter**: Doesn't survive restart; not distributed (acceptable for demo; Redis in Phase 9+)
- **HS256 symmetric key**: Compromise = token forgery until rotation (TTL 30 min limits window; RS256 future)
- **No MFA / step-up**: Phase 9+
- **No WAF / IP rate limit**: Deploy-time concern
- **Audit retention**: Unbounded growth; archival policy Phase 9+

### Risks Mitigated
| Risk | Mitigation |
|------|------------|
| Credential stuffing | Rate limiter + lockout audit |
| Token theft | Short TTL, rotation, HTTPS |
| Prompt injection | Fence + validator + budget |
| Data exfiltration | RBAC + object auth + no PII in prompts |
| Lineage mutation | No mutation endpoints + hash test |
| Internal spoofing | `X-Internal-Api-Key` + role `SERVICE` |

---

## Implementation Notes

### New Packages
```
com.sentinelflow.security
  ├─ SecurityConfig, SecurityProperties
  ├─ JwtTokenService, JwtAuthenticationWebFilter, InternalApiKeyWebFilter
  ├─ SecurityUser, SecurityUserRepository, SecurityDemoUsersInitializer
  ├─ LoginRateLimiter, AuthPrincipal, AuditEventService
  ├─ AuthController (/api/auth/login, /logout, /me)
  └─ TestAuth (test helper: real login → JWT → WebTestClient)
```

### Modified
- `AiInvestigationController`: audit BEFORE service call
- `SecurityConfig`: blanket `/api/investigations/**` rule
- 3 HTTP test classes: `OperationalApiTest`, `AiInvestigationDisabledApiTest`, `DatabaseOutageResilienceTest` → use `TestAuth`

### Frontend (React)
- `AuthProvider` + `session.ts` (sessionStorage + `auth-expired`/`auth-login` events)
- `RequireAuth` / `RequireRole` route guards
- `LoginPage` (demo credentials hint)
- `AppShell`: user badge, role badge, logout, role-gated nav
- `client.ts`: Bearer attachment; 401 → clear session + `auth-expired` event
- Tests: `LoginPage.test.tsx`, `AppShellAuth.test.tsx`, `renderWithAuth` helper

### Configuration
```yaml
sentinelflow:
  security:
    jwt-secret: ${JWT_SECRET}           # required prod
    jwt-ttl-seconds: ${JWT_TTL:1800}
    internal-api-key: ${INTERNAL_API_KEY}
    seed-demo-users: ${SEED_DEMO_USERS:true}
    rate-limit:
      max-attempts: ${RL_MAX:100}
      window-seconds: ${RL_WINDOW:60}
      cooldown-seconds: ${RL_COOLDOWN:0}
```

### Flyway Migrations
- V13: `security_users` table (id, username, password_hash, role, enabled, display_name, created_at)

---

## Verification

| Check | Result |
|-------|--------|
| Backend compile | ✅ |
| Backend tests (excl. benchmark) | 166/166 pass |
| Benchmark (PipelineLatencyBaselineTest) | p50=215ms p95=370ms (Phase 7: 192/315) |
| Frontend typecheck | ✅ |
| Frontend tests | 49/49 pass |
| Security test suite | 26/26 pass (auth, RBAC, object, immutable, sensitive, AI) |
| Rate limit test | ✅ (own context) |
| Prompt/Tool surface tests | ✅ |

---

## Rollback Plan

If critical issue found:
1. Revert `SecurityConfig` to pre-Phase-8 (permitAll on `/api/**` except auth)
2. Disable `JwtAuthenticationWebFilter` bean
3. Remove `AuthController`, `security_users` table (Flyway undo not needed — table isolated)
4. Frontend: remove `AuthProvider`, guards, `LoginPage`; revert `AppShell`, `client.ts`

All changes are additive or config-only; no domain logic mutated.

---

## Future Work (Phase 9+)

- Redis-backed distributed rate limiter
- RS256 asymmetric JWT + key rotation endpoint
- MFA / step-up authentication
- Audit log retention + archival (partition by month)
- WAF / IP rate limit at gateway
- SIEM integration (audit log streaming)
- Secrets rotation automation (Vault / SealedSecrets)

---

## References

- SECURITY-ARCHITECTURE.md
- THREAT-MODEL.md
- DOMAIN-BOUNDARIES.md (updated with §22 Security Domain)
- INV-1..18 (GitHub issues)