-- V11: deterministic demo fixtures for the Phase 5 Investigation Workspace.
--
-- Purpose: give the UI a real, processable transaction set matching the
-- Phase 4/5 demo narrative (txn-demo-001). The ML risk score and therefore the
-- final decision (ALLOW / REVIEW / BLOCK) are produced by the REAL pipeline +
-- ml-risk-service at runtime, so these fixtures only seed identity + merchant +
-- transaction rows with status RECEIVED. The investigator processes each
-- transaction through the intelligence pipeline from the UI; decision replay
-- then serves the persisted outcome. Nothing here fabricates a decision.
--
-- Scenario intent (documented, deterministic - amounts and identity signals):
--   txn-demo-001: user U1, known device + location, INR 12,000  -> narrative REVIEW
--   txn-demo-002: user U1, same device + location, INR 150       -> narrative ALLOW
--   txn-demo-003: user U1, same device + location, INR 900,000   -> narrative BLOCK
-- Actual decisions depend on the served model; the UI always displays the
-- persisted backend result, never a hardcoded value.
--
-- Idempotent: INSERT ... ON CONFLICT DO NOTHING on stable references.

-- Demo user
INSERT INTO users (id, external_reference, display_name, contact_reference, status, created_at, updated_at)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 'USR-DEMO-001', 'Demo Analyst User', 'demo@example.com', 'ACTIVE', NOW(), NOW())
ON CONFLICT (external_reference) DO NOTHING;

-- Known device and known location for the demo user (new-device / new-location
-- signals stay low so the amount dominates the model score)
INSERT INTO devices (id, user_id, device_reference, device_type, platform, first_seen_at, last_seen_at, created_at, updated_at)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb11', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 'DEV-DEMO-001', 'MOBILE', 'ANDROID', '2026-01-10 00:00:00+00', '2026-09-10 00:00:00+00', NOW(), NOW())
ON CONFLICT (device_reference) DO NOTHING;

INSERT INTO locations (id, user_id, country, region, city, latitude, longitude, first_seen_at, last_seen_at, created_at, updated_at)
VALUES ('cccccccc-cccc-cccc-cccc-ccccccccccc6', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 'IN', 'Karnataka', 'Bengaluru', 12.9716, 77.5946, '2026-01-10 00:00:00+00', '2026-09-10 00:00:00+00', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Demo merchant
INSERT INTO merchants (id, external_reference, name, category, country, status, created_at, updated_at)
VALUES ('dddddddd-dddd-dddd-dddd-ddddddddddd6', 'MRC-DEMO-001', 'DemoMarket', 'GROCERY', 'IN', 'ACTIVE', NOW(), NOW())
ON CONFLICT (external_reference) DO NOTHING;

-- Demo transactions (RECEIVED => awaiting the intelligence pipeline)
INSERT INTO transactions (id, transaction_reference, user_id, merchant_id, device_id, location_id, amount, currency, transaction_type, channel, transaction_timestamp, status, created_at, updated_at)
VALUES
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee9', 'txn-demo-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 'dddddddd-dddd-dddd-dddd-ddddddddddd6', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb11', 'cccccccc-cccc-cccc-cccc-ccccccccccc6', 12000.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-14 10:00:00+00', 'RECEIVED', NOW(), NOW()),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeec1', 'txn-demo-002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 'dddddddd-dddd-dddd-dddd-ddddddddddd6', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb11', 'cccccccc-cccc-cccc-cccc-ccccccccccc6', 150.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-14 10:15:00+00', 'RECEIVED', NOW(), NOW()),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeec2', 'txn-demo-003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 'dddddddd-dddd-dddd-dddd-ddddddddddd6', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb11', 'cccccccc-cccc-cccc-cccc-ccccccccccc6', 900000.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-14 11:00:00+00', 'RECEIVED', NOW(), NOW())
ON CONFLICT (transaction_reference) DO NOTHING;