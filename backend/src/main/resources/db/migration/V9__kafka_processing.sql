-- V9: Kafka processing attempts + outbox
CREATE TABLE kafka_processing_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(64) NOT NULL UNIQUE,
    transaction_reference VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64),
    event_type VARCHAR(32) NOT NULL,
    event_version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('RECEIVED','PROCESSING','SUCCEEDED','RETRYABLE_FAILURE','PERMANENT_FAILURE','DEAD_LETTERED')),
    attempt_count INTEGER NOT NULL DEFAULT 1,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kafka_attempts_txn_ref ON kafka_processing_attempts(transaction_reference);
CREATE INDEX idx_kafka_attempts_status ON kafka_processing_attempts(status);
CREATE INDEX idx_kafka_attempts_correlation ON kafka_processing_attempts(correlation_id);

-- Outbox for reliable publication (transactional outbox)
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_status ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);
