# Failure Matrix

> Every failure mode in SentinelFlow: trigger, detection, user impact, system
> behavior, metrics, and log pattern.

## Database Failures

| Failure | Trigger | Detection | HTTP Status | Behavior | Metrics | Log Pattern |
|---|---|---|---|---|---|---|
| **Connection exhaustion** | HikariPool saturated (all connections active/leasing) | `DataAccessException` wrapping `Connection is not available, request timed out` | 503 `DEPENDENCY_UNAVAILABLE` | Request rejected immediately; no pipeline execution; DB probe returns `UNAVAILABLE` | `db_status{status="unavailable"}` | `Database unavailable for transaction {ref}: Unable to acquire JDBC Connection` |
| **Query timeout** | Slow query exceeds Hikari connection timeout | `DataAccessException` wrapping timeout | 503 `DEPENDENCY_UNAVAILABLE` | Same as connection exhaustion | Same | Same |
| **Connection closed/stale** | Postgres restart or network blip | HikariPool validation (`Connection has been closed`) | 503 `DEPENDENCY_UNAVAILABLE` on next request; auto-recovery when pool refreshes | Connection evicted; next acquisition gets fresh connection | `db_status{status="healthy"}` after recovery | `Failed to validate connection ... Possibly consider using a shorter maxLifetime value` |

**Recovery:** HikariPool automatically evicts stale connections and creates new ones. No manual intervention needed.

## Kafka Failures

| Failure | Trigger | Detection | HTTP Status | Behavior | Metrics | Log Pattern |
|---|---|---|---|---|---|---|
| **Broker unavailable** | Kafka broker down or network partition | `KafkaException: broker unavailable` on publish | N/A (outbox relay retries) | `OutboxPublisher` logs warning and increments retry count; event stays in `PENDING` status; relay retries on next cycle | `kafka_retryable` incremented; `outbox_status{status="pending"}` | `Outbox publish failed id={id} error=org.apache.kafka.common.KafkaException: broker unavailable` |
| **Topic missing** | Topic not created or deleted | `UnknownTopicOrPartitionException` | N/A | Same as broker unavailable; permanent after max retries → `PERMANENT_FAILURE` → DLQ | `kafka_permanent` + `kafka_dead_lettered` | Similar KafkaException |
| **Consumer lag** | Slow processing or consumer group rebalancing | Kafka consumer lag metrics (external monitoring) | N/A | Events processed with increasing latency; no user-visible impact until backlog exceeds SLA | `kafka_consumed` rate slows | Consumer rebalance logs from Kafka client |
| **Consumer disconnected** | Network blip or broker restart | `Node N disconnected` + `Connection to node N could not be established` | N/A | Consumer group rebalances; processing pauses briefly then resumes | No counter impact (transient) | `[Consumer clientId=... groupId=...] Node N disconnected.` |

**Recovery:** Outbox relay automatically retries with exponential backoff. DLQ events are diagnostic only — no automatic re-drive.

## ML Service Failures

| Failure | Trigger | Detection | HTTP Status | Behavior | Metrics | Log Pattern |
|---|---|---|---|---|---|---|
| **Timeout** | ML service responds slowly (>2s) | `WebClientResponseException` or `TimeoutException` | 503 `ML_UNAVAILABLE` | Pipeline fails; transaction stays `RECEIVED`; no decision recorded | `ml_failure` incremented; `ml_status{status="unavailable"}` | `ML inference failed: ... timeout` |
| **Connection refused** | ML service down | `WebClientRequestException: finishConnect failed: Connection refused` | 503 `ML_UNAVAILABLE` | Same as timeout | Same | `ML inference failed: finishConnect(..) failed: Connection refused: ...` |
| **Malformed response** | ML returns unexpected JSON structure | Jackson deserialization error | 500 `INTERNAL_ERROR` | Pipeline fails; transaction stays `RECEIVED` | `ml_failure` | `ML inference failed: ... JSON parse error` |

**Recovery:** Transaction can be reprocessed after ML service recovers. No automatic retry on the pipeline level.

## AI Investigation Failures

| Failure | Trigger | Detection | HTTP Status | Behavior | Metrics | Log Pattern |
|---|---|---|---|---|---|---|
| **Disabled** | `AI_INVESTIGATOR_ENABLED=false` (default) | `AiUnavailableException` thrown before any work | 503 `AI_UNAVAILABLE` | Request rejected immediately; no persistence; audit trail not written | `ai_requests` incremented (no `ai_succeeded`/`ai_failed`) | `AI investigations are disabled` |
| **Provider unavailable** | LLM API key invalid, provider down, rate limited | `AiProviderUnavailableException` | 503 `AI_UNAVAILABLE` | Audit run persisted with `FAILED` status and error code; no explanation returned | `ai_failure` with code `AI_UNAVAILABLE` | `AI provider unavailable: ...` |
| **Response invalid** | LLM response fails semantic validation (evidence grounding, required fields) | `AiResponseInvalidException` | 502 `AI_RESPONSE_INVALID` | Audit run persisted with `FAILED`; no explanation returned | `ai_failure` with code `AI_RESPONSE_INVALID` | `AI response failed validation: ...` |
| **Budget exceeded** | LLM makes too many tool calls in one request | `ToolCallBudget` exhaustion | 503 `AI_UNAVAILABLE` | Tool budget terminated; audit run persisted; `translate()` wraps as unavailable | `ai_failure` with code `AI_UNAVAILABLE` | `AI investigation failed unexpectedly` |
| **Gateway not configured** | No AI gateway bean available | `AiUnavailableException` | 503 `AI_UNAVAILABLE` | Same as disabled | Same | `AI gateway is not configured` |

**Recovery:** Retry the same explanation request. The persisted decision is never affected.

## Pipeline Failures

| Failure | Trigger | Detection | HTTP Status | Behavior | Metrics | Log Pattern |
|---|---|---|---|---|---|---|
| **ML inference failure** | Any ML error (see ML section) | `MlInferenceException` | 503 `ML_UNAVAILABLE` | Pipeline throws `PipelineException`; transaction stays `RECEIVED` | `ml_failure` | `ML inference failed: ...` |
| **Feature generation failure** | Bug in feature extraction logic | `RuntimeException` in feature builder | 500 `INTERNAL_ERROR` | Pipeline fails; transaction stays `RECEIVED` | `transaction_failed` | `Pipeline failed for transaction {ref}: ...` |
| **Policy evaluation failure** | Invalid policy configuration | `PolicyEvaluationException` | 500 `INTERNAL_ERROR` | Pipeline fails; no decision recorded | `transaction_failed` | `Policy evaluation failed: ...` |

**Recovery:** Fix the underlying issue and reprocess the transaction.

## Metrics Reference

| Metric | Tags | Description |
|---|---|---|
| `transaction_processed` | — | Total pipeline invocations |
| `transaction_succeeded` | — | Successful pipeline completions |
| `transaction_failed` | — | Failed pipeline invocations |
| `transaction_decision` | `decision={ALLOW,REVIEW,BLOCK}` | Decisions by type |
| `ml_requests` | — | ML inference requests |
| `ml_success` | — | Successful ML inferences |
| `ml_failure` | — | Failed ML inferences |
| `ai_requests` | — | AI explanation requests |
| `ai_success` | — | Successful AI explanations |
| `ai_failure` | `code={error_code}` | Failed AI explanations by code |
| `outbox_status` | `status={PENDING,PUBLISHED,FAILED,...}` | Outbox row counts by status |

## Error Code Reference

| Code | Meaning | HTTP Status |
|---|---|---|
| `DB_UNAVAILABLE` | Database connection exhausted or timed out | 503 |
| `ML_UNAVAILABLE` | ML inference service unreachable or timed out | 503 |
| `AI_UNAVAILABLE` | AI investigation disabled or provider unreachable | 503 |
| `AI_RESPONSE_INVALID` | AI response failed validation (evidence grounding) | 502 |
| `AI_UNEXPECTED` | Unexpected error in AI pipeline | 503 |
| `DEPENDENCY_UNAVAILABLE` | Generic dependency failure (maps from DataAccessException) | 503 |
