CREATE TABLE ai_investigation_runs (
    id UUID PRIMARY KEY,
    investigation_id UUID NOT NULL REFERENCES investigations(id) ON DELETE CASCADE,
    request_type VARCHAR(32) NOT NULL,
    free_form_question TEXT,
    status VARCHAR(16) NOT NULL,
    provider VARCHAR(64),
    model VARCHAR(128),
    tool_call_count INTEGER NOT NULL DEFAULT 0,
    latency_ms BIGINT,
    correlation_id VARCHAR(64),
    response JSONB,
    error_code VARCHAR(32),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_investigation_runs_investigation
    ON ai_investigation_runs (investigation_id, created_at DESC);