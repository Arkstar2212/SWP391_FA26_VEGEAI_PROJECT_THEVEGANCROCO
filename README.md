<div align="center">

# 🌿 VEGEAI
### Intelligent Vegetarian & Vegan Nutrition Ecosystem — Backend Service

[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/Arkstar2212/SWP391_FA26_VEGEAI_PROJECT_THEVEGANCROCO)
[![Java](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Database](https://img.shields.io/badge/Database-PostgreSQL_16-4479A1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-Swagger_UI-85EA2D?style=for-the-badge&logo=swagger&logoColor=white)](http://localhost:8080/swagger-ui.html)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

<p align="center">
  <b>Dedicated enterprise backend for the VEGEAI platform, providing robust RESTful APIs, JWT stateless authentication, dual-token invalidation, brute-force protection, and deterministic nutrition calculation.</b>
</p>

[Swagger UI](http://localhost:8080/swagger-ui.html) • [OpenAPI Docs](http://localhost:8080/api-docs) • [ERD Diagram](docs/VEGEAI_CROWS_FOOT_ERD.drawio)

---

</div>

## 📌 Overview

This repository houses the **VEGEAI Backend Service**. Frontend clients (React Vite SPA / Mobile) run as independent repositories and consume this service via standardized REST APIs.

### 🛡️ Key Features & Security
- **Stateless JWT Authentication:** HMAC-SHA256 access tokens (15-min TTL) paired with database-backed refresh tokens (UUID, 1-day TTL).
- **Dual Invalidation:** Logout simultaneously blacklists the JWT access token and revokes the refresh token.
- **Brute-Force Rate Limiting:** In-memory Caffeine-based limiter blocks login attempts after 5 consecutive failures per identifier for 15 minutes.
- **Anti-Enumeration Protection:** Forgot password endpoint returns uniform response messages regardless of email existence.
- **Standardized API Envelope:** Every endpoint returns `ApiResponse<T>` with typed error codes and HTTP status codes.
- **CORS Configured:** Pre-configured for Vite frontend (`http://localhost:5173`) and Next/React (`http://localhost:3000`) with credential support (`HttpOnly` cookies).
- **Flexible Refresh Token Transport:** Supports both standard `HttpOnly` cookies and `X-Refresh-Token` request headers.

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
| `GET`  | `/api/auth/error-codes` | *(None)* | Developer reference of all error codes & HTTP statuses |

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
├── docs/                        # Architecture specs, PRD, and database ERD
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

### 2. Run with Maven Wrapper
**Windows:**
```powershell
.\mvnw.cmd spring-boot:run
```
**Linux / macOS:**
```bash
./mvnw spring-boot:run
```

### 3. Run Test Suite
```powershell
.\mvnw.cmd test
```

### 4. Interactive Documentation
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI 3 JSON:** `http://localhost:8080/api-docs`

---

## 🛡️ Responsible AI & Security Compliance

1. **Deterministic Separation:** Mathematical nutrition metrics are never generated via LLM inference; they use audited formulas.
2. **AI Audit Trail:** Every model invocation is permanently recorded with latency, token usage, and moderation flags.
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
