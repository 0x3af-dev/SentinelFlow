-- Phase 4 analytics: policy simulation and counterfactual analysis records.
--
-- These tables record analytical artifacts produced by the Policy Lab and the
-- Counterfactual Engine. They are append-only (no update statements anywhere in
-- the application). They never share rows with, or modify, the production
-- lineage tables (transactions, feature_snapshots, risk_scores, risk_factors,
-- decision_records, decision_policies, model_versions, evidence nodes/edges).

CREATE TABLE policy_simulations (
    id                    UUID PRIMARY KEY,
    transaction_reference VARCHAR(64)      NOT NULL,
    base_decision_id      UUID,
    policy_name           VARCHAR(64)      NOT NULL,
    policy_version        VARCHAR(32)      NOT NULL,
    review_threshold      DOUBLE PRECISION NOT NULL,
    block_threshold       DOUBLE PRECISION NOT NULL,
    base_risk_score       DOUBLE PRECISION NOT NULL,
    actual_decision       VARCHAR(16)      NOT NULL,
    simulated_decision    VARCHAR(16)      NOT NULL,
    decision_changed      BOOLEAN          NOT NULL,
    change_type           VARCHAR(24)      NOT NULL,
    explanation           VARCHAR(512),
    requested_by          VARCHAR(128),
    created_at            TIMESTAMPTZ      NOT NULL,
    updated_at            TIMESTAMPTZ      NOT NULL,
    CONSTRAINT fk_policy_simulations_base_decision
        FOREIGN KEY (base_decision_id) REFERENCES decision_records (id),
    CONSTRAINT chk_policy_simulations_review
        CHECK (review_threshold >= 0 AND review_threshold < 1),
    CONSTRAINT chk_policy_simulations_block
        CHECK (block_threshold > 0 AND block_threshold <= 1),
    CONSTRAINT chk_policy_simulations_review_lt_block
        CHECK (review_threshold < block_threshold),
    CONSTRAINT chk_policy_simulations_base_score
        CHECK (base_risk_score >= 0 AND base_risk_score <= 1),
    CONSTRAINT chk_policy_simulations_actual_decision
        CHECK (actual_decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    CONSTRAINT chk_policy_simulations_simulated_decision
        CHECK (simulated_decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    CONSTRAINT chk_policy_simulations_change_type
        CHECK (change_type IN ('UNCHANGED', 'MORE_PERMISSIVE', 'MORE_RESTRICTIVE'))
);

CREATE INDEX idx_policy_simulations_txn       ON policy_simulations (transaction_reference);
CREATE INDEX idx_policy_simulations_created   ON policy_simulations (created_at);

CREATE TABLE counterfactual_analyses (
    id                    UUID PRIMARY KEY,
    transaction_reference VARCHAR(64)      NOT NULL,
    feature_snapshot_id   UUID             NOT NULL,
    feature_schema_version VARCHAR(32)     NOT NULL,
    model_name            VARCHAR(64)      NOT NULL,
    model_version         VARCHAR(32)      NOT NULL,
    modifications         JSONB            NOT NULL,
    original_risk_score   DOUBLE PRECISION NOT NULL,
    hypothetical_risk_score DOUBLE PRECISION NOT NULL,
    score_delta           DOUBLE PRECISION NOT NULL,
    original_decision     VARCHAR(16)      NOT NULL,
    hypothetical_decision VARCHAR(16)      NOT NULL,
    decision_changed      BOOLEAN          NOT NULL,
    requested_by          VARCHAR(128),
    created_at            TIMESTAMPTZ      NOT NULL,
    updated_at            TIMESTAMPTZ      NOT NULL,
    CONSTRAINT fk_counterfactual_analyses_snapshot
        FOREIGN KEY (feature_snapshot_id) REFERENCES feature_snapshots (id),
    CONSTRAINT chk_counterfactual_analyses_original_score
        CHECK (original_risk_score >= 0 AND original_risk_score <= 1),
    CONSTRAINT chk_counterfactual_analyses_hypothetical_score
        CHECK (hypothetical_risk_score >= 0 AND hypothetical_risk_score <= 1),
    CONSTRAINT chk_counterfactual_analyses_original_decision
        CHECK (original_decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    CONSTRAINT chk_counterfactual_analyses_hypothetical_decision
        CHECK (hypothetical_decision IN ('ALLOW', 'REVIEW', 'BLOCK'))
);

CREATE INDEX idx_counterfactual_analyses_txn      ON counterfactual_analyses (transaction_reference);
CREATE INDEX idx_counterfactual_analyses_snapshot ON counterfactual_analyses (feature_snapshot_id);
CREATE INDEX idx_counterfactual_analyses_created  ON counterfactual_analyses (created_at);