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

