-- ============================================================
-- VEGEAI Database Initialization Script (PostgreSQL)
-- Run this ONCE on a fresh database.
-- JPA ddl-auto=update will manage schema after this.
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(15),
    date_of_birth DATE,
    gender VARCHAR(10),
    address VARCHAR(255),
    height_cm DOUBLE PRECISION,
    weight_kg DOUBLE PRECISION,
    bmi DOUBLE PRECISION,
    daily_calorie_target INTEGER,
    dietary_preferences VARCHAR(255),
    allergies VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    last_login TIMESTAMP WITHOUT TIME ZONE
);

-- Insert default ADMIN account (only via DB, never via API)
-- Raw Password: Admin@123 -> Client Pre-hashed SHA-256: e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7
-- BCrypt (rounds=12) of pre-hashed password
INSERT INTO users (
    user_id, username, email, password_hash, full_name,
    role, status, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'admin',
    'admin@vegeai.com',
    '$2a$12$jqsrfFJQi7chjYDlbu5Szu7Z29lPwi3LSRcRdPKuTSFBaIucutyhW', -- Admin@123
    'System Administrator',
    'ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- ── Email Verification Tokens (OTP) ──────────────────────────
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    otp_code VARCHAR(6) NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    purpose VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evt_user_purpose ON email_verification_tokens(user_id, purpose);

-- ── Refresh Tokens (Opaque UUID) ──────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token VARCHAR(36) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_value ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_tokens(user_id);

-- ============================================================
-- Smart Fridge Inventory Table (fridge_items)
-- ============================================================
CREATE TABLE IF NOT EXISTS fridge_items (
    item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    category VARCHAR(30) DEFAULT 'VEGAN',
    is_vegetarian BOOLEAN NOT NULL DEFAULT TRUE,
    quantity DOUBLE PRECISION,
    unit VARCHAR(30),
    freshness_status VARCHAR(20) NOT NULL DEFAULT 'FRESH',
    freshness_score DOUBLE PRECISION,
    first_scanned_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    last_scanned_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    estimated_shelf_life_days INTEGER DEFAULT 5,
    expiry_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes VARCHAR(500),
    storage_tip VARCHAR(255),
    confidence DOUBLE PRECISION,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fridge_user_status ON fridge_items(user_id, status);
CREATE INDEX IF NOT EXISTS idx_fridge_user_norm_name ON fridge_items(user_id, normalized_name, status);
CREATE INDEX IF NOT EXISTS idx_fridge_expiry ON fridge_items(expiry_date);


