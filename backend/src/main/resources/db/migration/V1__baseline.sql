-- V1: baseline / shared configuration
-- Enables pgcrypto for gen_random_uuid() defaults and documents UTC + naming conventions.
-- Conventions (Phase 1):
--   * tables: lowercase snake_case, plural
--   * PKs: UUID (id DEFAULT gen_random_uuid())
--   * timestamps: TIMESTAMPTZ stored in UTC
--   * money: NUMERIC(19,4), never float
--   * flexible payloads: JSONB only for features/policy config/evidence metadata/event payload

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
