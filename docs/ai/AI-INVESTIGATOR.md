# AI Investigator — Evidence-Grounded Explanations

Phase 6 adds an **AI investigation assistant** that answers structured and free-form
questions about an already-decided transaction using **only the evidence already
persisted** for that decision. It is a read-only analyst assistant: the LLM never
evaluates a policy, never scores a transaction, never mutates a decision, and never
sits on the transaction critical path.

The capability is **disabled by default** (`sentinelflow.ai.enabled=false`). With the
default configuration no API key is required, no OpenAI beans are created, and the
application boots identically to Phase 5. When enabled (`AI_INVESTIGATOR_ENABLED=true`
plus `AI_API_KEY` and `AI_BASE_URL`) it serves `POST
/api/investigations/{id}/explanations`.

---

## Non-Negotiables (spec + architecture)

1. **The AI never decides.** The persisted `DecisionRecord` is authoritative. The
   model is told this in the system prompt, its answer is structurally incapable of
   changing a decision, and the response validator re-checks every claim it makes
   about the decision against the persisted record.
2. **The AI never mutates production data.** The only tool surface it can reach is a
   set of read-only queries. Its only write is the audit trail row it is awarded.
3. **The AI never touches the transaction path.** The gateway is only reachable from
   the investigation API. Kafka processing, the pipeline, ML, rules, and policy never
   reference it.
4. **Every claim must be evidence-grounded.** Any statement about SentinelFlow data
   must cite `evidenceIds` that resolve to persisted evidence nodes. Unverifiable
   claims are rejected before they reach a human.
5. **Every run is audited.** Success *and* failure are appended to
   `ai_investigation_runs` with provider, model, tool call count, latency,
   correlation id, and either the validated response or the controlled error.
6. **Untrusted input is contained.** Free-form questions are embedded as untrusted
   data in the *user* segment; the system prompt explicitly instructs the model to
   never follow instructions found in data it did not receive from the platform.

## Module Layout (`com.sentinelflow.ai`)

| Layer | Classes | Responsibility |
|-------|---------|----------------|
| prompt | `PromptAssembler` | Builds the immutable system prompt (hard rules), evidence-grounding taxonomy, and the per-request question templates. |
| gateway | `AiGateway`, `SpringAiGateway` | Provider boundary. `SpringAiGateway` wraps a Spring AI `ChatClient` with the read-only tool surface and classifies failures. |
| tool | `InvestigationAiTools`, `ToolCallBudget`, `ToolViews` | The model's entire read-only surface (summary, timeline, risk decision replay, evidence graph, policy simulations, counterfactuals) gated by a strict per-request budget. |
| validation | `ExplanationValidator` | Structural + semantic validation of the model answer against the persisted decision/evidence universe before it can be returned. |
| config | `SpringAiConfig`, `AiProperties`, `SentinelFlowAiConfiguration` | Conditional wiring. Chat model is built **only** when enabled; the OpenAI auto-configurations are excluded so no API key is required at boot. |
| model/repo | `AiInvestigationRun`, `AiInvestigationRunRepository` | Immutable audit trail (`ai_investigation_runs`). |
| service | `AiInvestigationService` | Orchestrates one run; owns the invariant that nothing is returned unvalidated and nothing fails unaudited. |

## Request Flow

```
POST /api/investigations/{id}/explanations
        │
        ▼
AiInvestigationService.explain(id, request)
  1 requireEnabled()                      → 503 AI_UNAVAILABLE when disabled
  2 summary(id) + timeline(id) + decisionReplay(id)
                                          → 404 when the investigation is missing
  3 allowGrounding(replay, summary)       → the allowed evidence/simulation/counterfactual universe,
                                            the persisted decision, risk score, and policy reference
  4 toolCallBudget.begin()
  5 assembler.assemble(summary, timeline, policy, requestType, freeForm)
  6 gateway.generate(systemPrompt, userQuestion, tools)   ← model call (synchronous)
  7 toolCallBudget.end()                  → always, in a finally block
  8 validator.validate(answer, allowed)   → rejects fabricated/contradicting/unsupported output
  9 persistSuccess(...)                   → SUCCEEDED run row; return answer
  ✗ any failure                           → persistFailure(...) with the controlled code;
                                            rethrow AI_UNAVAILABLE / AI_RESPONSE_INVALID
```

Synchronous generation (on the requesting thread) keeps the per-request
`ToolCallBudget` (a `ThreadLocal`) correct and makes the total latency a normal,
auditable request metric. Per-request provider timeouts are delegated to the provider
client's network timeouts; the total budget and per-investigation-rate are bounded by
`ToolCallBudget` and the tool call maximums in `AiProperties`.

## The Evidence-Grounding Protocol

The model may only assert things it can support with persisted artifacts:

- **FACTS** must cite `evidenceIds` that exist in the persisted evidence graph.
- **INFERENCE** must cite the evidence it reasons from and is phrased as inference.
- **HYPOTHESIS** must cite what it would test and is phrased as a recommendation
  (`collect X`, never `X did happen`).
- **UNKNOWN** is the only acceptable answer when the model has no evidence.

Fabricated evidence IDs, a `recordedDecision`/`recordedRiskScore` that contradicts the
persisted `DecisionRecord`, a `decisionPolicy` that does not match the persisted
policy (or is non-empty when no policy exists), simulation/counterfactual IDs outside
the recorded artifacts, missing disclaimers on hypotheticals, or action-language that
violates the "never decide" rule — all fail validation and produce a `FAILED` run with
`error_code=AI_RESPONSE_INVALID` (HTTP 502). Nothing is returned to the user.

## Definitions of Resilience

- **Disabled** (`enabled=false`): every request fails fast with `AI_UNAVAILABLE`
  (503). No provider is configured, so there is nothing to reach.
- **Provider failure** (`AiProviderUnavailableException`): unreachable, timeout,
  rate-limit. `FAILED` run with `AI_UNAVAILABLE` (503); production data untouched.
- **Bad provider output** (`AiProviderResponseException`): malformed JSON, budget
  exhaustion, schema violation. `FAILED` run with `AI_RESPONSE_INVALID` (502).
- **Validation rejection** (`AiResponseInvalidException`): the model *answered* but
  broke the evidence rules. `FAILED` run with `AI_RESPONSE_INVALID` (502).
- **Unexpected**: wrapped as `AI_UNAVAILABLE`; the run is still recorded, the
  exception is logged, and the API never exposes a stack trace.

## Configuration

```yaml
sentinelflow:
  ai:
    enabled: ${AI_INVESTIGATOR_ENABLED:false}     # OFF by default
    provider: ${AI_PROVIDER:openai-compatible}    # informational
    model: ${AI_MODEL:gpt-4o-mini}
    api-key: ${AI_API_KEY:}
    base-url: ${AI_BASE_URL:https://api.openai.com/v1}
    timeout-seconds: ${AI_TIMEOUT_SECONDS:30}
    max-tool-calls: ${AI_MAX_TOOL_CALLS:12}          # budget: total tool calls/run
    max-same-tool-calls: ${AI_MAX_SAME_TOOL_CALLS:3} # budget: repeats of one tool
    max-result-rows: ${AI_MAX_RESULT_ROWS:50}         # no unbounded reads to the model
    max-evidence-nodes: ${AI_MAX_EVIDENCE_NODES:100}  # prompt truncation guard
    max-history-events: ${AI_MAX_HISTORY_EVENTS:100}
    max-output-tokens: ${AI_MAX_OUTPUT_TOKENS:1500}
    max-observations: ${AI_MAX_OBSERVATIONS:20}        # answer-shape caps
    max-findings: ${AI_MAX_FINDINGS:20}
    max-evidence-references: ${AI_MAX_EVIDENCE_REFERENCES:100}
    max-recommended-evidence: ${AI_MAX_RECOMMENDED_EVIDENCE:10}
```

## API

| Method | Path | Returns | Errors |
|--------|------|---------|--------|
| `POST` | `/api/investigations/{id}/explanations` | `InvestigationExplanation` (200) | `AI_UNAVAILABLE` 503, `AI_RESPONSE_INVALID` 502, not-found 404, validation 400 |
| `GET` | `/api/investigations/{id}/explanations/runs` | `AiInvestigationRun[]` (audit trail) | 404 |

The frontend consumes this through `investigationsApi.explain` /
`explanationRuns` and renders it as the **AI investigator** panel with the `AI
analysis` stamp, evidence-resolution markers, and the run trail. The UI makes no
decision logic of its own (ADR-018).

## Limitations

- 1.1.8 of Spring AI does not expose a per-request `OpenAiChatOptions.timeout`;
  provider timeout tuning relies on the provider client and on the configured output
  budget. Documented here so an upgrade path is clear.
- The model is as good as the evidence given to it; evidence *not* in the graph
  simply cannot be cited, by construction.
- The answer is a snapshot in time: it reflects the evidence and decision at request
  time and is frozen in the audit row.

## Tests

- Unit: `PromptAssemblerTest`, `ExplanationValidatorTest`, `ToolCallBudgetTest`,
  `InvestigationAiToolsTest`.
- Integration: `AiInvestigationServiceTest` (scriptable fake gateway — happy path
  with no-production-mutation counts, provider failure → `AI_UNAVAILABLE`, fabricated
  evidence / wrong decision → `AI_RESPONSE_INVALID`, budget exhaustion, simulated
  artifact mismatch) and `AiInvestigationDisabledApiTest` (no gateway bean, 503, no
  OpenAI auto-config, boots with no API key).