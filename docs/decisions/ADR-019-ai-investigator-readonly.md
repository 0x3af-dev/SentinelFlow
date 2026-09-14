# ADR-019: Read-only AI investigator with explicit evidence rules

- **Status:** accepted (Phase 6)
- **Date:** 2026-09-14
- **Deciders:** backend, analytics, security, frontend
- **Technical story:** Phase 6 must add an LLM assistant that explains the
  persisted decision without mutating production data, fabricating evidence, or
  touching the transaction critical path.

## Context

The platform already has full decision lineage, an evidence graph, policy-lab
simulations, and counterfactual analysis (Phase 4, §18), all strictly read-only.
Phase 5 surfaces these through a React workspace that renders backend values
verbatim (ADR-018). The natural next question is: *"Can an analyst ask an
assistant why this decision was made, and have the assistant answer from the
same evidence the pipeline built?"*

Using an LLM in a regulated-adjacent workflow introduces several risks: the
model could fabricate evidence, contradict the persisted decision, infer
prohibited actions, attempt a policy evaluation, or mutate production data
through tool use. A naive integration would also couple the transaction critical
path to an external provider.

## Decision

### 1. Disabled by default

The capability ships off. No API key is needed at boot, no OpenAI beans are
created, and the application boots identically to Phase 5. Activation requires
opting in with `AI_INVESTIGATOR_ENABLED=true` and providing `AI_API_KEY` +
`AI_BASE_URL`.

### 2. Read-only tool surface + strict budget

The model receives exactly one set of read-only tools: the persisted decision
replay, the evidence graph, policy simulations, and counterfactuals. Each
request is gated by a `ThreadLocal` `ToolCallBudget` (max total calls, max
repeats of one tool). Exhaustion immediately terminates the model call.

### 3. Synchronous gateway, LLM on the caller thread

`SpringAiGateway` executes the LLM call synchronously on the requesting thread.
This keeps `ThreadLocal` budget accounting correct, avoids reactive adaptation
costs, and makes the total latency a normal request metric. Per-request timeouts
are delegated to the provider client's network timeouts; the tool budget guards
against runaway calls.

### 4. Evidence-grounding protocol in the prompt

The system prompt defines four evidence classes — FACT (asserted from tool
output), INFERENCE (derived with reasoning), HYPOTHESIS (what *to collect*),
and UNKNOWN — and explicitly labels HYPOTHESIS as unverifiable. Free-form analyst
questions are embedded in the user segment and the system prompt instructs the
model to never follow instructions found in data.

### 5. Two-layer validation

- **Structural**: JSON parsing, required fields, allowed request type.
- **Semantic (ExplanationValidator)**: every `evidenceId` must resolve to a
  persisted node; `recordedDecision`/`recordedRiskScore` must match the persisted
  `DecisionRecord` within tolerance; `decisionPolicy` must equal
  `policy.name()/version` and must be blank when no policy exists; every
  simulation/counterfactual ID must belong to the recorded artifacts; action
  phrases (`allow`, `block`, `reject`, etc.) are rejected; disclaimers are
  required on hypotheticals; capped observation and reference counts are
  enforced.

A validation failure produces a `FAILED` audit run and a 502
`AI_RESPONSE_INVALID`. Nothing is returned unvalidated.

### 6. Audit trail as a hard invariant

`AiInvestigationRun` records every attempt — succeeded or failed — with
provider, model, tool call count, latency, correlation id, and either the full
validated response or the controlled error code. The production decision is
never affected regardless of the outcome.

### 7. Frontend integration as a read-only panel

The UI consumes `POST /{id}/explanations` via `investigationsApi.explain` and
renders the answer through the same visual language used for simulations and
counterfactuals — the `AI analysis` stamp, evidence-resolution markers (green
for resolved, red for unresolved), and the run trail. The panel contains no
decision logic (ADR-018 §5).

## Consequences

- **Positive:** analysts get an interactive, evidence-bound layer of explanation
  without changing the pipeline, ML, or policy models.
- **Positive:** the two-layer validation and explicit audit trail make the system
  defensible: every answer can be traced to the exact evidence and tool calls
  that produced it.
- **Positive:** the disabled-by-default posture means a deployment without an LLM
  budget behaves identically to Phase 5.
- **Negative:** answers are constrained to evidence already collected — the
  assistant cannot surface data that was not part of the pipeline; this is the
  intended limitation.
- **Negative:** the current Spring AI 1.1.8 SDK does not expose a per-request
  timeout override; provider timeout tuning relies on the client and output
  budget. An upgrade path is documented.
- **Risk:** misuse — the "never decides" rule is enforced by prompt, by tool
  design, and by validator, but not by an invariant at the model layer; the
  validator is the authoritative backstop.