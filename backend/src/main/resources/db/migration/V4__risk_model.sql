-- V4: risk + model domain
-- Model versions and policy versions are immutable history: never overwrite, only add new versions.
-- Feature snapshots are immutable per evaluation for replay/counterfactuals.

CREATE TABLE model_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name VARCHAR(128) NOT NULL,
    version VARCHAR(32) NOT NULL,
    algorithm VARCHAR(128),
    feature_schema_version VARCHAR(32),
    training_dataset_reference VARCHAR(255),
    artifact_reference VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE'
        CONSTRAINT chk_model_versions_status CHECK (status IN ('TRAINING','EVALUATION','CANDIDATE','ACTIVE','RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    CONSTRAINT uq_model_name_version UNIQUE (model_name, version)
);

CREATE TABLE feature_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    feature_schema_version VARCHAR(32),
    features JSONB,
    generated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feature_snapshots_transaction_id ON feature_snapshots (transaction_id);

CREATE TABLE risk_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    model_version_id UUID NOT NULL REFERENCES model_versions(id),
    risk_score DOUBLE PRECISION NOT NULL
        CONSTRAINT chk_risk_scores_range CHECK (risk_score >= 0 AND risk_score <= 1),
    prediction VARCHAR(16),
    inference_timestamp TIMESTAMPTZ,
    inference_latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_scores_transaction_id ON risk_scores (transaction_id);
CREATE INDEX idx_risk_scores_model_version_id ON risk_scores (model_version_id);

-- Risk factors are separate observations; factor_type intentionally unconstrained
-- to allow evolution without schema changes.
CREATE TABLE risk_factors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    factor_type VARCHAR(64) NOT NULL,
    description TEXT,
    severity VARCHAR(16),
    source VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_factors_transaction_id ON risk_factors (transaction_id);
CREATE INDEX idx_risk_factors_factor_type ON risk_factors (factor_type);
