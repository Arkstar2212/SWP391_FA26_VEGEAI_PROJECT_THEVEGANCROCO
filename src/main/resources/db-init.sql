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

-- Insert default ACTIVE MEMBER account
-- Raw Password: Member@123 -> Client Pre-hashed SHA-256: abe2d3ed5419e1a2293c034a6b375a622ff5a60e5ac30f29c461220898ffdd97
INSERT INTO users (
    user_id, username, email, password_hash, full_name,
    role, status, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'member',
    'member@vegeai.com',
    '$2a$12$ExqNZqpItOOtg.qlSGV7H.E7DtOJP7ARFMoGucjt9hWg0xsYvCHZa', -- Member@123
    'Standard Member',
    'MEMBER',
    'ACTIVE',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- Insert default PENDING_VERIFICATION MEMBER account
-- Raw Password: User@123 -> Client Pre-hashed SHA-256: 3e7c19576488862816f13b512cacf3e4ba97dd97243ea0bd6a2ad1642d86ba72
INSERT INTO users (
    user_id, username, email, password_hash, full_name,
    role, status, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'unverified',
    'unverified@vegeai.com',
    '$2a$12$K9dnVbrhnnZuIoX3yW3Y2elVaEit5xAO.LrVIO3q14th2itnJ8s0u', -- User@123
    'Unverified Member',
    'MEMBER',
    'PENDING_VERIFICATION',
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

-- ============================================================
-- Tags & Taxonomy Table (tags)
-- ============================================================
CREATE TABLE IF NOT EXISTS tags (
    tag_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    tag_group VARCHAR(30) NOT NULL,
    description VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_tags_code ON tags(code);
CREATE INDEX IF NOT EXISTS idx_tags_group ON tags(tag_group);

-- ============================================================
-- Verified Recipes Table (recipes)
-- ============================================================
CREATE TABLE IF NOT EXISTS recipes (
    recipe_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(150) NOT NULL,
    slug VARCHAR(160) NOT NULL UNIQUE,
    description VARCHAR(1000),
    cook_time_minutes INTEGER,
    prep_time_minutes INTEGER,
    servings INTEGER,
    difficulty VARCHAR(20) DEFAULT 'EASY',
    calories INTEGER,
    protein_grams DOUBLE PRECISION,
    carbs_grams DOUBLE PRECISION,
    fat_grams DOUBLE PRECISION,
    fiber_grams DOUBLE PRECISION,
    image_url VARCHAR(500),
    is_verified BOOLEAN NOT NULL DEFAULT TRUE,
    author_id UUID REFERENCES users(user_id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_recipes_slug ON recipes(slug);
CREATE INDEX IF NOT EXISTS idx_recipes_status ON recipes(status);
CREATE INDEX IF NOT EXISTS idx_recipes_calories ON recipes(calories);
CREATE INDEX IF NOT EXISTS idx_recipes_cook_time ON recipes(cook_time_minutes);

-- ============================================================
-- Recipe Ingredients Table (recipe_ingredients)
-- ============================================================
CREATE TABLE IF NOT EXISTS recipe_ingredients (
    ingredient_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id UUID NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    amount DOUBLE PRECISION,
    unit VARCHAR(30),
    is_optional BOOLEAN NOT NULL DEFAULT FALSE,
    is_allergen BOOLEAN NOT NULL DEFAULT FALSE,
    allergen_type VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_recipe_ing_norm_name ON recipe_ingredients(normalized_name);
CREATE INDEX IF NOT EXISTS idx_recipe_ing_recipe ON recipe_ingredients(recipe_id);

-- ============================================================
-- Recipe Tags Mapping Table (recipe_tags)
-- ============================================================
CREATE TABLE IF NOT EXISTS recipe_tags (
    recipe_id UUID NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (recipe_id, tag_id)
);

-- ============================================================
-- Recipe Cooking Steps Table (recipe_instructions)
-- ============================================================
CREATE TABLE IF NOT EXISTS recipe_instructions (
    recipe_id UUID NOT NULL REFERENCES recipes(recipe_id) ON DELETE CASCADE,
    step_description VARCHAR(1000) NOT NULL,
    step_order INTEGER NOT NULL,
    PRIMARY KEY (recipe_id, step_order)
);




-- ============================================================
-- Nutrition Profiles (ERD: NUTRITION.PROFILES) — one per user
-- Inputs + deterministic snapshot of BMI / BMR / TDEE / macro targets
-- ============================================================
CREATE TABLE IF NOT EXISTS nutrition_profiles (
    nutrition_profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    gender VARCHAR(10) NOT NULL,
    date_of_birth DATE NOT NULL,
    height_cm DOUBLE PRECISION NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    activity_level VARCHAR(20) NOT NULL,
    goal VARCHAR(20) NOT NULL,
    custom_daily_calories INTEGER,
    bmi DOUBLE PRECISION,
    bmi_category VARCHAR(20),
    bmr INTEGER,
    tdee INTEGER,
    daily_calories_target INTEGER,
    protein_target_g INTEGER,
    carbs_target_g INTEGER,
    fat_target_g INTEGER,
    fiber_target_g INTEGER,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_nutrition_profiles_user ON nutrition_profiles(user_id);
