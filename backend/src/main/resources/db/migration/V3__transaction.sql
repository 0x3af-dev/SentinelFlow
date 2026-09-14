-- V3: merchant + transaction domain
-- Money: NUMERIC(19,4), amount >= 0. Currency explicit VARCHAR(3).
-- Lifecycle status is processing state, NOT fraud classification.

CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_reference VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(128),
    country VARCHAR(2),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_merchants_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merchants_status ON merchants (status);
CREATE INDEX idx_merchants_category ON merchants (category);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_reference VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    device_id UUID REFERENCES devices(id),
    location_id UUID REFERENCES locations(id),
    amount NUMERIC(19,4) NOT NULL CONSTRAINT chk_transactions_amount_non_negative CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL,
    transaction_type VARCHAR(32),
    channel VARCHAR(32),
    transaction_timestamp TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED'
        CONSTRAINT chk_transactions_status CHECK (status IN ('RECEIVED','ENRICHING','SCORING','DECIDED','COMPLETED','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_merchant_id ON transactions (merchant_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_timestamp ON transactions (transaction_timestamp);
