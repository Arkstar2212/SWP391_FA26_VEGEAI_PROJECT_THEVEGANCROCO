-- ============================================================
-- VEGEAI Database Initialization Script (PostgreSQL)
-- Run this ONCE on a fresh database.
-- JPA ddl-auto=update will manage schema after this.
-- ============================================================

-- Insert default ADMIN account (only via DB, never via API)
-- Password: Admin@123 (BCrypt hash — change this before production!)
-- Generate new hash: https://bcrypt-generator.com/ with rounds=12
INSERT INTO users (
    user_id, username, email, password_hash, full_name,
    role, status, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'admin',
    'admin@vegeai.com',
    '$2a$12$K5PBiT0jS.6p/3r1.Xtm7e5TxcHVE0Y2mG9K7vJh3sAZ0uFDZrW2', -- Admin@123
    'System Administrator',
    'ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;
