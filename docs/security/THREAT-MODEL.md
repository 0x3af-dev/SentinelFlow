# SentinelFlow Phase 8 — Threat Model (STRIDE)

## System Overview

SentinelFlow is a fraud investigation platform. Phase 8 adds authentication, authorization, and governance. The attack surface is the public `/api` surface (React frontend proxied via dev gateway) and the internal `/internal` Kafka relay endpoint.

**Trust Boundaries**:
1. **Internet → Frontend (React)**: Same-origin via dev proxy; production via CDN/WAF
2. **Frontend → Backend `/api`**: Authenticated (JWT Bearer) or public (`/api/auth/**`, `/actuator/health`, `/actuator/info`)
3. **Backend → Internal `/internal`**: `X-Internal-Api-Key` header; only Kafka consumer in production
4. **Backend → PostgreSQL**: Trusted network; credentials via env vars/secrets
5. **Backend → ML Service (Python)**: Internal network; request/response validated
6. **Backend → AI Provider (OpenAI)**: Optional; disabled by default; outbound HTTPS only

---

## STRIDE Analysis

### Spoofing

| Threat | Asset | Mitigation | Residual |
|--------|-------|------------|----------|
| Attacker presents stolen JWT | All authenticated endpoints | Short TTL (30 min), HS256, stateless; token invalid on secret rotation | Token theft window ≤ TTL |
| Attacker guesses `internal-api-key` | `/internal/**` enqueue | 32+ char random secret; rate-limited by consumer; no in-process callers | Brute-force infeasible |
| Attacker spoofs `requestedBy` in payload | Investigation creation, AI explanations, policy sim | Server derives actor from `AuthPrincipal` (JWT); client value ignored | None |
| Attacker registers as another user | `security_users` | No self-registration endpoint; seeded admin only; BCrypt | None |

### Tampering

| Threat | Asset | Mitigation | Residual |
|--------|-------|------------|----------|
| Attacker modifies decision record | `decision_records` | No PUT/PATCH/DELETE endpoints; pipeline only writes; DB FK + application layer | None |
| Attacker deletes audit entries | `audit_logs` | No DELETE endpoints; append-only; `AuditLogRepository` no `delete` method | None |
| Attacker injects malicious AI prompt | AI explanation | Untrusted-data fence; `ExplanationValidator` rejects action language; tool budget | Partial — model may still hallucinate within rules |
| Attacker modifies feature snapshot | `feature_snapshots` | No mutation endpoints; feature domain append-only | None |
| Attacker alters policy simulation result | `policy_simulations` | Append-only table; `PolicyLabService` validates thresholds before write | None |

### Repudiation

| Threat | Asset | Mitigation | Residual |
|--------|-------|------------|----------|
| Analyst denies creating investigation | `investigations`, `investigation_events` | `actor_username` from JWT in `investigation_events`; `AuditLog` records `AUTHENTICATION_SUCCESS` | Analyst could claim token stolen (bounded by TTL) |
| Operator denies internal enqueue | `kafka_processing_attempts` | `X-Internal-Api-Key` verified; attempt logged with correlation ID | Key compromise = full repudiation (rotate regularly) |
| AI explanation denied | `ai_investigation_runs` | Append-only run record with correlation ID, tool calls, provider response | Provider response not stored (only metadata); correlation ID links to run |

### Information Disclosure

| Threat | Asset | Mitigation | Residual |
|--------|-------|------------|----------|
| Unauthenticated user reads transactions | `/api/transactions/**` | `SecurityConfig` → all `/api/**` except auth/actuator require authentication | None |
| Operator reads investigations | `/api/investigations/**` | Blanket `.pathMatchers("/api/investigations/**").hasAnyRole("ANALYST","INVESTIGATOR","ADMIN")` | None |
| Analyst reads system health | `/actuator/metrics/**` | OPERATOR/ADMIN only | None |
| PII in AI prompts | OpenAI request | `PromptAssembler` uses only synthetic IDs, amounts, risk factors; no names, PANs, SSNs | Provider may log prompts (out of scope) |
| JWT secret in logs / config | `SecurityProperties` | Never logged; test value only in `application-test.yml`; prod via env var | Config management out of scope |
| BCrypt hashes exposed | `security_users` | No endpoint exposes users table; repo no `findAll` in API | DB access out of scope |

### Denial of Service

| Threat | Asset | Mitigation | Residual |
|--------|-------|------------|----------|
| Login brute force | `POST /api/auth/login` | `LoginRateLimiter`: per-username max attempts/window/cooldown; lockout audit | Distributed attack across many usernames (mitigate via WAF / IP rate limit) |
| AI prompt flooding | `POST /api/investigations/*/explanations` | Tool budget (6 calls); `ExplanationValidator`; `AI_RESPONSE_INVALID` stops generation; audit trail | Budget exhaustion returns 502; no resource exhaustion on backend |
| Policy-lab simulation flood | `POST /api/policy-lab/simulate` | Auth required (ANALYST+); threshold validation; append-only table | Table growth unbounded (monitor / retention policy future) |
| Counterfactual flood | `POST /api/counterfactuals` | Auth required; bounded feature registry (18 features); ML client call per request | ML service latency × concurrent requests (mock in test; prod: queue + circuit breaker) |
| Kafka consumer overload | `/internal/kafka/transactions/*/enqueue` | `X-Internal-Api-Key`; consumer group scaling; outbox relay bounds | Backpressure via attempt retries + DLQ |

### Elevation of Privilege

| Threat | Asset | Mitigation | Residual |
|--------|-------|------------|----------|
| Analyst accesses `/internal/**` | Internal Kafka enqueue | `InternalApiKeyWebFilter` requires header; role check `SERVICE` or `ADMIN` | None (analyst has no key) |
| Operator accesses investigations | `/api/investigations/**` | Blanket RBAC rule `hasAnyRole(ANALYST, INVESTIGATOR, ADMIN)` | None |
| Analyst escalates to ADMIN | Role in JWT | Role from `security_users` table (seeded); no self-service role change | DB compromise = full escalation |
| AI tool used to read unauthorized data | `InvestigationAiTools` | Tools accept `investigationId` → service verifies ownership via `InvestigationApplicationService` | Service bug = bypass (covered by object-auth test) |

---

## Data Flow Diagram (Text)

```
[Browser] --(JWT)--> [Frontend React] --(Bearer)--> [Spring WebFlux /api]
                                                              |
                                                              |-- /api/auth/** (permitAll)
                                                              |-- /actuator/health,info (permitAll)
                                                              |-- /api/operations/**, /actuator/metrics/** (OPERATOR+)
                                                              |-- /internal/** (SERVICE + X-Internal-Api-Key)
                                                              |-- /api/investigations/** (ANALYST/INVESTIGATOR/ADMIN)
                                                              |-- /api/policy-lab/**, /api/counterfactuals, /api/transactions/*/process (ANALYST/INVESTIGATOR/ADMIN)
                                                              |-- /api/investigations/*/explanations (ANALYST/INVESTIGATOR/ADMIN)
                                                              |
                                                              v
                                                    [Service Layer]
                                                              |
                                                              |-- Auth: SecurityUserRepository (BCrypt) + JwtTokenService (HS256)
                                                              |-- Auth: LoginRateLimiter (in-memory)
                                                              |-- Auth: AuditEventService (append-only)
                                                              |-- Investigation: InvestigationApplicationService (object auth)
                                                              |-- AI: AiInvestigationService (PromptAssembler, AiGateway, Tools, Validator, Audit)
                                                              |-- Analytics: PolicyLabService, CounterfactualService, DecisionReplayService (read-only)
                                                              |-- Pipeline: TransactionIntelligencePipeline (orchestration only)
                                                              |
                                                              v
                                                    [PostgreSQL]
                                                              |-- security_users (auth)
                                                              |-- audit_logs, ai_investigation_runs (audit)
                                                              |-- investigations, investigation_events (workflow)
                                                              |-- transactions, merchants, users, devices, locations (domain)
                                                              |-- feature_snapshots, risk_scores, risk_factors, model_versions (risk)
                                                              |-- decision_records, decision_policies (decision)
                                                              |-- evidence_nodes, evidence_edges (evidence)
                                                              |-- policy_simulations, counterfactual_analyses (analytics)
                                                              |-- outbox_events, kafka_processing_attempts (kafka)
```

---

## Attack Trees (Top 3)

### 1. Steal Analyst Token → Read Investigations
```
Steal JWT
  ├─ XSS on frontend (CSP, React auto-escape, no dangerouslySetInnerHTML)
  ├─ MITM (HTTPS only in prod; dev proxy same-origin)
  ├─ Log leakage (tokens never logged)
  └─ Token reuse (TTL 30 min; rotate secret)
```
**Likelihood**: Low  
**Impact**: Investigation data read (no mutation)  
**Mitigation**: Short TTL, HTTPS, CSP, no token logging

### 2. Prompt Injection → AI Data Exfiltration
```
Inject "ignore instructions" in free-form question
  ├─ Reaches system prompt? → Blocked by untrusted-data fence
  ├─ Reaches tool call? → Tool budget + validation reject
  ├─ Model hallucinates action? → ExplanationValidator rejects action language
  └─ Model returns PII? → PromptAssembler never includes PII
```
**Likelihood**: Low  
**Impact**: Model output may leak training data (provider responsibility)  
**Mitigation**: Fence, validation, budget, disabled by default

### 3. Internal Key Compromise → Fraudulent Transaction Processing
```
Obtain X-Internal-Api-Key
  ├─ Call /internal/kafka/transactions/{ref}/enqueue
  ├─ Consumer processes → pipeline runs → decision recorded
  └─ Decision appears in system
```
**Likelihood**: Low (key rotation, no in-process callers)  
**Impact**: Spurious decision records (auditable, reversible via investigation)  
**Mitigation**: Key rotation, attempt logging, DLQ, no direct DB mutation

---

## Security Test Coverage Map

| STRIDE | Test Class | Key Assertions |
|--------|------------|----------------|
| Spoofing | `SecurityIntegrationTestSuite` (auth) | 401 without token; 401 with invalid token; 403 wrong role |
| Tampering | `SecurityIntegrationTestSuite` (immutable) | Table hashes unchanged after full workflow; no DELETE/PUT/PATCH endpoints |
| Repudiation | `SecurityIntegrationTestSuite` (audit) | AI attempt logged even when frozen; lockout audited; actor from JWT |
| Info Disclosure | `SecurityIntegrationTestSuite` (authz) | Operator 403 on investigations; Analyst 403 on metrics; unauth 401 |
| DoS | `LoginRateLimitTest` | 3 failures → 429; lockout audit; cooldown enforced |
| EoP | `SecurityIntegrationTestSuite` (object) | Analyst cannot read other's investigation; Admin bypasses; OPERATOR 403 |

---

## Residual Risks (Accepted)

| Risk | Rationale |
|------|-----------|
| In-memory `LoginRateLimiter` doesn't survive restart | Acceptable for demo; production would use Redis + distributed lock |
| No MFA / step-up auth | Phase 8 scope is stateless JWT RBAC; MFA is Phase 9+ |
| AI provider (OpenAI) may log prompts | Disabled by default; production must evaluate provider DPA |
| No WAF / IP rate limit on `/api/auth/login` | Deploy-time concern (Cloudflare, AWS WAF, nginx) |
| `audit_logs` unbounded growth | Retention policy + archiving (Phase 9+) |
| HS256 symmetric key — compromise = token forgery | Rotation via env var; TTL 30 min limits window; RS256 future |

---

## Compliance Notes

- **GDPR/CCPA**: No PII stored in `security_users` (username only); transaction PII in separate domain; AI never receives PII; audit logs contain usernames (pseudonymous) — lawful basis: legitimate interest (fraud prevention)
- **SOC 2**: Append-only audit trail; immutable lineage; role-based access; encryption at rest (PostgreSQL TLS + disk encryption — infra responsibility)
- **PCI DSS**: Not in scope (no PAN storage in SentinelFlow; transaction amounts only)

---

## Incident Response Runbook (Summary)

| Incident | Detection | Response |
|----------|-----------|----------|
| JWT secret compromised | Unusual 401 patterns; token validation failures | Rotate `JWT_SECRET` env var → rolling restart; all sessions invalidated within 30 min |
| Internal API key compromised | Unexpected enqueue attempts; consumer lag | Rotate `INTERNAL_API_KEY` → restart backend + consumer |
| Brute force login | `AUTHENTICATION_LOCKED_OUT` audit spike | WAF IP block; increase `rate-limit.max-attempts` / `cooldown-seconds` |
| AI injection attempt | `AI_RESPONSE_INVALID` spike in `ai_investigation_runs` | Review prompts; tighten `ExplanationValidator`; disable AI (`enabled=false`) |
| Audit tampering suspected | Hash mismatch in immutable test / manual check | DB forensic; restore from backup; investigate access logs |

---

## Review Cadence

- **Per release**: Run full security test suite (166 backend + 49 frontend)
- **Quarterly**: Rotate `JWT_SECRET`, `INTERNAL_API_KEY`; review audit log growth
- **Annually**: Threat model refresh; penetration test (scope: `/api` surface)