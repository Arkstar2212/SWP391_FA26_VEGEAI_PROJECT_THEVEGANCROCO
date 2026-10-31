<div align="center">

# 🌿 VEGEAI
### Intelligent Vegetarian & Vegan Nutrition Ecosystem — Backend Service

[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/Arkstar2212/SWP391_FA26_VEGEAI_PROJECT_THEVEGANCROCO)
[![Java](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Database](https://img.shields.io/badge/Database-PostgreSQL_16-4479A1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-Swagger_UI-85EA2D?style=for-the-badge&logo=swagger&logoColor=white)](http://localhost:8081/swagger-ui.html)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

<p align="center">
  <b>Dedicated enterprise backend for the VEGEAI platform, providing robust RESTful APIs, JWT stateless authentication, dual-token invalidation, brute-force protection, and deterministic nutrition calculation.</b>
</p>

[Swagger UI](http://localhost:8081/swagger-ui.html) • [OpenAPI Docs](http://localhost:8081/api-docs) • [Crow's Foot ERD](docs/erd-diagram-v2.svg) • [📖 Developer Handover & Setup Guide](BACKEND_HANDOVER_AND_SETUP_GUIDE.md) • [📋 Flow Logic & Requirements Specification](VEGEAI_BACKEND_FLOW_REQUIREMENTS_GUIDE.md)

---

</div>

## 📌 Overview

This repository houses the **VEGEAI Backend Service**. Frontend clients (React Vite SPA / Mobile) run as independent repositories and consume this service via standardized REST APIs.

### 🛡️ Key Features & Security Standards
- **Client SHA-256 Pre-Hashing:** Passwords must be pre-hashed with SHA-256 (64-character hex regex `^[A-Fa-f0-9]{64}$`) on the client side before transmission; backend applies BCrypt rounds before database persistence.
- **Dual-Token Session Management:** HMAC-SHA256 access tokens (15-min TTL) paired with database-backed refresh tokens (UUID, 1-7 days TTL, `HttpOnly` cookie with `SameSite=Strict; Path=/api/auth`).
- **Distributed Redis Blacklist:** Logout invalidates both tokens: marks refresh token as revoked in PostgreSQL and pushes the JWT access token to Spring Data Redis with its exact remaining TTL.
- **Distributed Brute-Force Rate Limiting (Bucket4j + Redis):**
  - **Username/Email:** Max 5 failed attempts in 15 minutes (counter resets upon successful login).
  - **Client-IP (`X-Forwarded-For` / `RemoteAddr`):** Max 20 failed attempts in 15 minutes.
  - Exceeding threshold returns `429 Too Many Requests` (`LOGIN_ATTEMPTS_EXCEEDED`).
- **Guest AI Chatbot Quota Guard (`/api/ai/chat`):** Limits unauthenticated guest users to 3 requests per 24 hours per Client-IP via Redis; registered members have unlimited access.
- **Anti-Enumeration Protection:** Forgot password endpoint returns uniform response messages regardless of email existence.
- **Standardized API Envelope:** Every endpoint returns `ApiResponse<T>` with typed error codes and HTTP status codes.
- **CORS Configured:** Pre-configured for Vite frontend (`http://localhost:5173`) and Next/React (`http://localhost:3000`) with credential support (`HttpOnly` cookies).

---

## 📡 Authentication API Matrix

All authentication endpoints are located under `/api/auth`:

| Method | Endpoint | Request Body | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | `RegisterRequest` | Registers member (`PENDING_VERIFICATION`) & issues 6-digit OTP |
| `POST` | `/api/auth/verify-email` | `VerifyEmailRequest` | Verifies 6-digit OTP and activates account (`ACTIVE`) |
| `POST` | `/api/auth/resend-otp` | `ResendOtpRequest` | Resends verification OTP (30s cooldown enforced) |
| `POST` | `/api/auth/login` | `LoginRequest` | Returns access token + sets `refreshToken` HttpOnly cookie |
| `POST` | `/api/auth/refresh` | *(Cookie or Header)* | Rotates access token via HttpOnly cookie or `X-Refresh-Token` header |
| `POST` | `/api/auth/logout` | *(Bearer + Cookie/Header)* | Dual invalidation: blacklists access token + revokes refresh token |
| `POST` | `/api/auth/forgot-password`| `ForgotPasswordRequest` | Sends reset OTP (anti-enumeration protected) |
| `POST` | `/api/auth/reset-password` | `ResetPasswordRequest` | Verifies OTP and resets account password |

### Standard Response Envelope (`ApiResponse<T>`)
```json
{
  "success": true,
  "code": 200,
  "message": "Operation successful.",
  "data": { ... },
  "timestamp": "2026-10-06T11:25:00Z"
}
```

### Error Response Envelope
```json
{
  "success": false,
  "code": 1001,
  "message": "Username must be between 4 and 50 characters.",
  "data": {
    "username": "Username must be between 4 and 50 characters."
  },
  "timestamp": "2026-10-06T11:25:00Z"
}
```

---

## 📁 Repository Structure

```text
SWP391_FA26_VEGEAI_PROJECT/
├── .github/
│   └── workflows/ci.yml         # Automated GitHub Actions test pipeline
├── docs/                        # Architecture specs, PRD, and Crow's Foot ERD (erd-diagram-v2.svg)
├── src/
│   ├── main/
│   │   ├── java/com/vegeai/backend/
│   │   │   ├── VegeaiApplication.java
│   │   │   ├── common/          # Config, Security, JWT Filter, ApiResponse, Exceptions
│   │   │   └── modules/
│   │   │       ├── auth/        # AuthController, AuthService, DTOs, Entities, Repositories
│   │   │       ├── user/        # UserController, UserService, User Entity
│   │   │       └── admin/       # AdminController (user management)
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db-init.sql
│   └── test/                    # Integration and unit test suite
├── .env.example                 # Template for local environment variables
├── pom.xml                      # Maven project configuration
├── mvnw / mvnw.cmd              # Maven wrapper
└── README.md
```

## 🗺️ OpenStreetMap Places & In-App Notifications

Place search uses OpenStreetMap community-contributed data through Overpass, and Nominatim geocodes search terms when coordinates are omitted. Results are limited to places carrying vegetarian/vegan-related OSM tags; coverage varies by area. OSM does not supply Google-style ratings or live opening status, so `rating`, `userRatingsTotal`, and `isOpenNow` may be `null`. `isVerified` means that OSM tags explicitly describe vegetarian/vegan-only cuisine; it is not an independent VEGEAI inspection. The frontend must show the returned `attribution` (© OpenStreetMap contributors).

Place API routes remain under `/api/places`: `/nearby`, `/search`, `/categories`, `/verified/nearby`, and `/verified/search`. Search accepts optional `category=ALL|RESTAURANT|SUPERMARKET|GROCERY_STORE`.

Authenticated in-app notifications are available through `GET /api/notifications?page=0&size=20`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read`, and `PATCH /api/notifications/read-all`. Reads and updates are scoped to the authenticated recipient. Notifications are created for profile/nutrition and fridge/meal-plan activity, blog and question moderation, question answers, and video comments/favorites. Hibernate creates the `user_notifications` table from the entity mapping.

OpenStreetMap endpoints can be overridden with `APP_OPENSTREETMAP_OVERPASS_URL` and `APP_OPENSTREETMAP_NOMINATIM_URL`. Respect provider rate limits; the backend caches place queries for one hour.

### AI operations and blog moderation

Admin AI telemetry is available from `/api/admin/ai/status` and `/api/admin/ai/audit-logs`. It records operation, model, status, duration, moderation outcome, and safe error categories (HTTP status or root-cause type) without storing prompts or submitted blog text. Blog moderation allows up to 75 seconds for the provider response; if the provider still fails, the post remains in the Admin queue for manual review. Existing databases are updated through Hibernate schema `update`.

Member blog submissions enter the existing Admin review queue immediately while AI safety screening runs in the background. Workers claim each check atomically with a two-minute lease, preventing duplicate provider calls across backend instances while allowing abandoned claims to be retried. Member-requested edits to published posts and Admin edits to pending content clear stale AI results and queue a fresh check. A flagged or unavailable result is shown to the reviewer; the AI never publishes or deletes a post. Admin approval/rejection remains the final decision, and late results from an expired claim or older content version are ignored.

### Community ratings and comments

Shared content-interaction endpoints accept `BLOG`, `RECIPE`, and `VIDEO` targets. Before deploying this change to an existing PostgreSQL database, apply `scripts/migrate-video-content-interactions.sql` so its target-type constraints allow video ratings and comments, then apply `scripts/migrate-content-review-metadata.sql` for review tags, photos, star scores, and public-name preferences. Review photos are stored in the persistent `vegeai_content_uploads` Docker volume (or the directory configured by `CONTENT_UPLOAD_DIR`).

---

## 🗄️ Database Architecture & Crow's Foot ERD

The complete enterprise database schema follows textbook **Crow's Foot notation** (`||`, `o|`, `|<`, `o<`), organized into **24 tables across 5 functional zones** with dedicated relational highway corridors:

- **Zone 1: Account, Authentication & Biometrics** (`roles`, `accounts`, `user_health_profiles`, `otp_verifications`, `refresh_tokens`)
- **Zone 2: Recipe Management & Vegetarian Places (Map API)** (`categories`, `recipes`, `recipe_ingredients`, `vegan_shops`, `shop_menu_items`)
- **Zone 3: Smart Pantry & Vision AI Fridge Scanner** (`ingredients`, `user_pantries`, `fridge_scans`, `detected_ingredients`)
- **Zone 4: Social Community, Videos & AI Moderation** (`videos`, `video_summaries`, `blogs`, `comments`, `votes`, `ai_moderation_logs`)
- **Zone 5: Personalized Nutrition Planning & AI Chatbot Copilot** (`meal_plans`, `meal_plan_items`, `ai_chatbot_sessions`, `ai_chatbot_messages`)

🔗 **Full Vector Crow's Foot ERD Diagram:** [`docs/erd-diagram-v2.svg`](docs/erd-diagram-v2.svg)

---

## ⚡ Local Setup & Running

### Prerequisites
- **Java:** JDK 21 LTS
- **Database:** PostgreSQL 15+ (or H2 in-memory profile for testing)

### 1. Configure Environment
Copy `.env.example` to `.env` or set environment variables:
```bash
cp .env.example .env
```
Default connection (`src/main/resources/application.properties`):
- URL: `jdbc:postgresql://localhost:5432/vegeai`
- Username: `postgres`
- Password: `your_password`

### 2. Run with Docker Compose (Recommended for Full Stack)
Start PostgreSQL 16, Redis 7, and VEGEAI backend simultaneously:
```bash
docker compose up -d --build
```

### 3. Or Run Locally with Maven Wrapper
**Windows:**
```powershell
.\mvnw.cmd spring-boot:run
```
**Linux / macOS:**
```bash
./mvnw spring-boot:run
```

### 4. Run Test Suite
```powershell
.\mvnw.cmd test
```

### Data persistence and backup

Docker Compose keeps PostgreSQL, Redis and uploaded community media in named volumes. Rebuilding the backend image does not remove them. Do not run `docker compose down -v` unless you intentionally want to delete all local data.

Before the first restart after enabling the upload volume, or before any database migration, create a backup:

```powershell
.\scripts\Backup-VegeaiData.ps1
```

Backups are written to `backups/<timestamp>` and are excluded from Git. They contain the PostgreSQL dump and `/app/uploads` files. To restore an inspected backup deliberately:

```powershell
.\scripts\Restore-VegeaiData.ps1 -BackupDirectory .\backups\<timestamp> -ConfirmRestore
```

Restore replaces the current database and uploaded files. User-created blog posts, videos, profiles and other runtime data are never stored in Git.

### 5. Interactive Documentation
- **Swagger UI:** `http://localhost:8081/swagger-ui.html`
- **OpenAPI 3 JSON:** `http://localhost:8081/api-docs`

---

## 🛡️ Responsible AI & Security Compliance

1. **Deterministic Separation:** Mathematical nutrition metrics are never generated via LLM inference; they use audited formulas.
2. **AI Audit Trail:** AI operations record status and latency without persisting prompts, token counts, or personal content.
3. **Data Privacy:** Sensitive biometric metrics (weight, health conditions, allergies) are isolated to user scope and never exposed across accounts.
4. **Allergen Hard Constraint:** When the Meal Planner suggests recipes, hard allergen exclusions always override recommendation scoring.

---

## 👥 Course & Team Information

- **Course:** SWP391 — Software Development Project
- **Cohort / Class:** SE1912 | Semester: Fall 2026 / Spring 2026
- **Project Name:** VEGEAI (Vegetarian AI Platform)
- **Supervision & Mentorship:** FPT University Software Engineering Department

---

## 📄 License

This project is licensed under the terms of the [MIT License](LICENSE).
