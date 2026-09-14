-- V8: deterministic seed data for Phase 1 demos
-- All references are stable; run idempotently via migration (INSERT ... ON CONFLICT DO NOTHING)
-- or as a separate SQL script. Here we use a migration for reproducibility.

-- Users
INSERT INTO users (id, external_reference, display_name, contact_reference, status, created_at, updated_at) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'USR-SEED-001', 'Aarav Sharma', 'aarav@example.com', 'ACTIVE', NOW(), NOW()),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'USR-SEED-002', 'Diya Patel', 'diya@example.com', 'ACTIVE', NOW(), NOW()),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'USR-SEED-003', 'Kabir Rao', 'kabir@example.com', 'ACTIVE', NOW(), NOW()),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'USR-SEED-004', 'Meera Nair', 'meera@example.com', 'ACTIVE', NOW(), NOW()),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'USR-SEED-005', 'Rohan Desai', 'rohan@example.com', 'SUSPENDED', NOW(), NOW())
ON CONFLICT (external_reference) DO NOTHING;

-- Devices (all devices first, including unseen ones)
INSERT INTO devices (id, user_id, device_reference, device_type, platform, first_seen_at, last_seen_at, created_at, updated_at) VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'DEV-SEED-001', 'MOBILE', 'ANDROID', '2026-01-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'DEV-SEED-002', 'DESKTOP', 'WEB', '2026-06-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'DEV-SEED-003', 'MOBILE', 'IOS', '2026-02-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'DEV-SEED-004', 'MOBILE', 'ANDROID', '2026-03-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb5', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'DEV-SEED-005', 'DESKTOP', 'WEB', '2026-04-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb9', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'DEV-SEED-UNSEEN', 'MOBILE', 'ANDROID', '2026-09-02 14:25:00+00', '2026-09-02 14:25:00+00', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb10', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'DEV-SEED-SUSPENDED', 'MOBILE', 'ANDROID', '2026-09-06 21:55:00+00', '2026-09-06 21:55:00+00', NOW(), NOW())
ON CONFLICT (device_reference) DO NOTHING;

-- Locations
INSERT INTO locations (id, user_id, country, region, city, latitude, longitude, first_seen_at, last_seen_at, created_at, updated_at) VALUES
  ('cccccccc-cccc-cccc-cccc-ccccccccccc1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'IN', 'Karnataka', 'Bengaluru', 12.9716, 77.5946, '2026-01-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc2', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'IN', 'Maharashtra', 'Mumbai', 19.0760, 72.8777, '2026-02-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc3', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'IN', 'Delhi', 'New Delhi', 28.6139, 77.2090, '2026-03-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc4', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'IN', 'Tamil Nadu', 'Chennai', 13.0827, 80.2707, '2026-04-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW()),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc5', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'IN', 'Telangana', 'Hyderabad', 17.3850, 78.4867, '2026-05-01 00:00:00+00', '2026-09-01 00:00:00+00', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Merchants
INSERT INTO merchants (id, external_reference, name, category, country, status, created_at, updated_at) VALUES
  ('dddddddd-dddd-dddd-dddd-ddddddddddd1', 'MRC-SEED-001', 'FreshMart', 'GROCERY', 'IN', 'ACTIVE', NOW(), NOW()),
  ('dddddddd-dddd-dddd-dddd-ddddddddddd2', 'MRC-SEED-002', 'TechWorld', 'ELECTRONICS', 'IN', 'ACTIVE', NOW(), NOW()),
  ('dddddddd-dddd-dddd-dddd-ddddddddddd3', 'MRC-SEED-003', 'FashionHub', 'APPAREL', 'IN', 'ACTIVE', NOW(), NOW()),
  ('dddddddd-dddd-dddd-dddd-ddddddddddd4', 'MRC-SEED-004', 'FuelStop', 'FUEL', 'IN', 'ACTIVE', NOW(), NOW()),
  ('dddddddd-dddd-dddd-dddd-ddddddddddd5', 'MRC-SEED-005', 'DineOut', 'RESTAURANT', 'IN', 'ACTIVE', NOW(), NOW())
ON CONFLICT (external_reference) DO NOTHING;

-- Transactions (6 scenarios)
-- 1. Normal scenario
INSERT INTO transactions (id, transaction_reference, user_id, merchant_id, device_id, location_id, amount, currency, transaction_type, channel, transaction_timestamp, status, created_at, updated_at) VALUES
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 'TXN-SEED-NORMAL-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'dddddddd-dddd-dddd-dddd-ddddddddddd1', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 1499.50, 'INR', 'PURCHASE', 'ONLINE', '2026-09-01 10:00:00+00', 'COMPLETED', NOW(), NOW()),
-- 2. New-device scenario (known user, previously unseen device)
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2', 'TXN-SEED-NEWDEVICE-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'dddddddd-dddd-dddd-dddd-ddddddddddd2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb9', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 2999.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-02 14:30:00+00', 'COMPLETED', NOW(), NOW()),
-- 3. High-velocity scenario (multiple txns short interval)
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3', 'TXN-SEED-VELOCITY-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'dddddddd-dddd-dddd-dddd-ddddddddddd1', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 499.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-03 09:00:00+00', 'COMPLETED', NOW(), NOW()),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4', 'TXN-SEED-VELOCITY-002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'dddddddd-dddd-dddd-dddd-ddddddddddd3', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 599.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-03 09:02:00+00', 'COMPLETED', NOW(), NOW()),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee5', 'TXN-SEED-VELOCITY-003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'dddddddd-dddd-dddd-dddd-ddddddddddd4', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 349.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-03 09:04:00+00', 'COMPLETED', NOW(), NOW()),
-- 4. Unusual-location scenario
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee6', 'TXN-SEED-LOCATION-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'dddddddd-dddd-dddd-dddd-ddddddddddd5', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4', 'cccccccc-cccc-cccc-cccc-ccccccccccc5', 899.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-04 18:00:00+00', 'COMPLETED', NOW(), NOW()),
-- 5. High-value scenario
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee7', 'TXN-SEED-HIGHVALUE-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'dddddddd-dddd-dddd-dddd-ddddddddddd2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb5', 'cccccccc-cccc-cccc-cccc-ccccccccccc4', 89999.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-05 11:00:00+00', 'COMPLETED', NOW(), NOW()),
-- 6. Combined scenario (multiple suspicious characteristics)
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee8', 'TXN-SEED-COMBINED-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'dddddddd-dddd-dddd-dddd-ddddddddddd3', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb10', 'cccccccc-cccc-cccc-cccc-ccccccccccc5', 45999.00, 'INR', 'PURCHASE', 'ONLINE', '2026-09-06 22:00:00+00', 'COMPLETED', NOW(), NOW())
ON CONFLICT (transaction_reference) DO NOTHING;

-- Model version
INSERT INTO model_versions (id, model_name, version, algorithm, feature_schema_version, training_dataset_reference, artifact_reference, status, created_at, activated_at) VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff1', 'risk-model', 'v1', 'xgboost', 'fs-v1', 'ds-2026-q1', 's3://models/risk-model/v1', 'ACTIVE', NOW(), NOW())
ON CONFLICT (model_name, version) DO NOTHING;

-- Policy version
INSERT INTO decision_policies (id, policy_name, version, description, configuration, status, created_at, activated_at) VALUES
  ('00000000-0000-0000-0000-000000000001', 'fraud-policy', 'v1', 'Default fraud decision policy', '{"review_threshold": 0.5, "block_threshold": 0.85}', 'ACTIVE', NOW(), NOW())
ON CONFLICT (policy_name, version) DO NOTHING;