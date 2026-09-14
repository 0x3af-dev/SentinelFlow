-- V6: evidence graph
-- Nodes represent evidence artifacts; edges represent typed relationships.
-- Both source_type and node_type use controlled values via CHECK constraints.
-- relationship_type also constrained for consistency.

CREATE TABLE evidence_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_type VARCHAR(32) NOT NULL
        CONSTRAINT chk_evidence_nodes_node_type CHECK (node_type IN (
            'TRANSACTION','USER','DEVICE','LOCATION','MERCHANT',
            'BEHAVIOR','FEATURE','MODEL_PREDICTION','RISK_FACTOR',
            'RULE_RESULT','POLICY','DECISION','INVESTIGATION','EVENT'
        )),
    source_type VARCHAR(32) NOT NULL
        CONSTRAINT chk_evidence_nodes_source_type CHECK (source_type IN (
            'TRANSACTION_RECORD','USER_ACTIVITY','DEVICE_HISTORY','LOCATION_HISTORY',
            'MODEL_OUTPUT','RULE_ENGINE','POLICY_ENGINE','EXTERNAL_PROVIDER','INVESTIGATOR'
        )),
    entity_type VARCHAR(32),
    entity_id UUID,
    observed_at TIMESTAMPTZ,
    value JSONB,
    confidence DOUBLE PRECISION,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evidence_nodes_entity ON evidence_nodes (entity_type, entity_id);
CREATE INDEX idx_evidence_nodes_type ON evidence_nodes (node_type);
CREATE INDEX idx_evidence_nodes_observed_at ON evidence_nodes (observed_at);

CREATE TABLE evidence_edges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_node_id UUID NOT NULL REFERENCES evidence_nodes(id),
    target_node_id UUID NOT NULL REFERENCES evidence_nodes(id),
    relationship_type VARCHAR(32) NOT NULL
        CONSTRAINT chk_evidence_edges_rel_type CHECK (relationship_type IN (
            'PERFORMED_BY','USED_DEVICE','OCCURRED_AT','GENERATED_FEATURE',
            'USED_BY_MODEL','PRODUCED','CONTRIBUTES_TO','GOVERNED_BY',
            'ASSOCIATED_WITH','SUPPORTS','CONTRADICTS','REFERENCES'
        )),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_evidence_edge_unique UNIQUE (source_node_id, target_node_id, relationship_type)
);

CREATE INDEX idx_evidence_edges_source ON evidence_edges (source_node_id);
CREATE INDEX idx_evidence_edges_target ON evidence_edges (target_node_id);