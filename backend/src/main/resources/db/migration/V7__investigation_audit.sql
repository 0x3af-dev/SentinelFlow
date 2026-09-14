-- V7: investigation + audit
-- Investigation status and resolution are separate concepts.
-- Investigation events are append-only history (never overwritten).
-- Audit log is generic entity-level audit trail.

CREATE TABLE investigations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_reference VARCHAR(64) NOT NULL UNIQUE,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        CONSTRAINT chk_investigations_status CHECK (status IN ('OPEN','INVESTIGATING','RESOLVED')),
    priority VARCHAR(16) DEFAULT 'MEDIUM'
        CONSTRAINT chk_investigations_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    assigned_to VARCHAR(128),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolution VARCHAR(32)
        CONSTRAINT chk_investigations_resolution CHECK (resolution IN ('CONFIRMED_FRAUD','FALSE_POSITIVE','INCONCLUSIVE')),
    resolution_notes TEXT
);

CREATE INDEX idx_investigations_transaction_id ON investigations (transaction_id);
CREATE INDEX idx_investigations_status ON investigations (status);
CREATE INDEX idx_investigations_assigned_to ON investigations (assigned_to);

CREATE TABLE investigation_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id UUID NOT NULL REFERENCES investigations(id),
    event_type VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_reference VARCHAR(128),
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investigation_events_investigation_id ON investigation_events (investigation_id);
CREATE INDEX idx_investigation_events_timestamp ON investigation_events (event_timestamp);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type VARCHAR(32) NOT NULL,
    actor_reference VARCHAR(128),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID,
    previous_state JSONB,
    new_state JSONB,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB,
    correlation_reference VARCHAR(64)
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_type, actor_reference);
CREATE INDEX idx_audit_logs_correlation ON audit_logs (correlation_reference);