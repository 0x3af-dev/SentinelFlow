-- V5: decision + policy domain
-- Policy versions are immutable history (UNIQUE policy_name + version).
-- risk_score_id / policy_id are nullable to support future rule-only or manual
-- decision paths; the standard ML+policy path populates both.

CREATE TABLE decision_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_name VARCHAR(128) NOT NULL,
    version VARCHAR(32) NOT NULL,
    description TEXT,
    configuration JSONB,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT chk_decision_policies_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    CONSTRAINT uq_policy_name_version UNIQUE (policy_name, version)
);

CREATE TABLE decision_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    risk_score_id UUID REFERENCES risk_scores(id),
    policy_id UUID REFERENCES decision_policies(id),
    final_decision VARCHAR(16) NOT NULL
        CONSTRAINT chk_decision_records_decision CHECK (final_decision IN ('ALLOW','REVIEW','BLOCK')),
    decision_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decision_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_decision_records_transaction_id ON decision_records (transaction_id);
CREATE INDEX idx_decision_records_decision ON decision_records (final_decision);
CREATE INDEX idx_decision_records_timestamp ON decision_records (decision_timestamp);
