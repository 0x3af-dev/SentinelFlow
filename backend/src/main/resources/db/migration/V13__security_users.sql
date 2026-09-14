-- V13: platform security users + AI run actor attribution (Phase 8)
-- security_users are the AUTHENTICATION principals of the platform (analysts,
-- investigators, operators, admins). They are intentionally separate from the
-- transactions `users` table, which models transaction counterparties and is
-- NOT an authentication store. Passwords are stored as BCrypt hashes only.
-- Demo/dev principals are seeded at application startup by
-- SecurityDemoUsersInitializer and are never stored in a migration.

CREATE TABLE security_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL
        CONSTRAINT chk_security_users_role CHECK (role IN ('ANALYST','INVESTIGATOR','OPERATOR','ADMIN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    display_name VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_users_role ON security_users (role);

-- Attribute every AI investigation run to the authenticated principal that
-- requested it. Nullable so internal/system callers and historical rows remain
-- valid.
ALTER TABLE ai_investigation_runs ADD COLUMN actor_username VARCHAR(64);