# 🌿 VEGEAI BACKEND — DEVELOPER HANDOVER & SETUP GUIDE
> **Tài liệu bàn giao kỹ thuật & Hướng dẫn thiết lập môi trường Backend cho lập trình viên kế nhiệm.**  
> *Dự án: SWP391 - VEGEAI (Nền tảng hỗ trợ Dinh dưỡng Chay & Lối sống Thực vật Thông minh)*  
> *Phiên bản hiện tại: 1.0.0 • Java 21 LTS • Spring Boot 3.3.5 • PostgreSQL 16 • Redis 7*

---

## 📌 MỤC LỤC
1. [Tổng quan trạng thái hiện tại (Handover Status)](#1-tổng-quan-trạng-thái-hiện-tại-handover-status)
2. [Hướng dẫn cài đặt môi trường Local (Step-by-Step)](#2-hướng-dẫn-cài-đặt-môi-trường-local-step-by-step)
3. [Ma trận API đã hoàn thành (API Reference & Security)](#3-ma-trận-api-đã-hoàn-thành-api-reference--security)
   - [A. Phân hệ Authentication (`/api/auth`)](#a-phân-hệ-authentication-apiauth)
   - [B. Phân hệ User Profile & Health (`/api/users`)](#b-phân-hệ-user-profile--health-apiusers)
   - [C. Phân hệ Super Admin Control Center (`/api/admin`)](#c-phân-hệ-super-admin-control-center-apiadmin)
4. [Hướng dẫn & Đặc tả tích hợp AI Chatbot Prototype (`/api/ai/chat`)](#4-hướng-dẫn--đặc-tả-tích-hợp-ai-chatbot-prototype-apiaichat)
5. [Tài khoản kiểm thử mặc định (Test Credentials)](#5-tài-khoản-kiểm-thử-mặc-định-test-credentials)
6. [Lộ trình phát triển tiếp theo (Next Sprints Roadmap)](#6-lộ-trình-phát-triển-tiếp-theo-next-sprints-roadmap)
7. [Các nguyên tắc cốt lõi cần tuân thủ (Core Master Rules)](#7-các-nguyên-tắc-cốt-lõi-cần-tuân-thủ-core-master-rules)

---

## 1. TỔNG QUAN TRẠNG THÁI HIỆN TẠI (HANDOVER STATUS)

Dự án VEGEAI Backend được thiết kế theo kiến trúc **Domain-Driven Modular Monolith** với mục tiêu hiệu năng cao, chi phí tài nguyên thấp (< 350MB RAM khi chạy local), đảm bảo tính toàn vẹn giao dịch ACID mà không cần phức tạp hóa thành Microservices.

### Các phân hệ đã hoàn thiện 100%:
1. **Phân hệ Xác thực & Bảo mật (Authentication & Security):** Đầy đủ 8 endpoints đăng ký OTP, xác thực email, đăng nhập brute-force guard, dual-token xoay vòng (Access Token + HttpOnly Refresh Cookie), blacklist token qua Redis, quên mật khẩu chống dò quét tài khoản.
2. **Phân hệ Người dùng & Sức khỏe (User Management & Deterministic BMI):** Hồ sơ cá nhân, cập nhật dinh dưỡng/dị ứng, **tính toán chỉ số BMI tất định** theo công thức y khoa, đổi mật khẩu và thu hồi phiên đăng nhập từ xa, hủy tài khoản an toàn, public profile không lộ PII.
3. **Phân hệ Quản trị viên Super Admin & AI Ops (Admin Center):** Phân trang lọc đa tiêu chí người dùng ([`PageResponse`](src/main/java/com/vegeai/backend/common/dto/PageResponse.java)), khóa/mở khóa có áp dụng **Admin Immunity Rule**, phân quyền vai trò, thống kê toàn sàn và giám sát trạng thái mô hình AI (FR-ADMIN-03).
4. **AI Chatbot Prototype:** Controller `/api/ai/chat` sẵn sàng kết nối FE với bộ lọc giới hạn Guest 3 lượt/24h qua IP filter.
5. **Bộ kiểm thử tự động (Unit & Integration Tests):** Đã kiểm thử tự động toàn diện **39/39 tests (PASS 100%)** với database bộ nhớ trong H2.

---

## 2. HƯỚNG DẪN CÀI ĐẶT MÔI TRƯỜNG LOCAL (STEP-BY-STEP)

### 2.1 Yêu cầu hệ thống (Prerequisites)
- **JDK 21 LTS** (Khuyên dùng Eclipse Temurin, Oracle OpenJDK hoặc Amazon Corretto 21).
- **Maven 3.9+** (Dự án đã tích hợp sẵn `./mvnw` và `./mvnw.cmd`, không bắt buộc cài Maven toàn cục).
- **Docker Desktop** (Khuyên dùng để chạy nhanh PostgreSQL 16 và Redis 7).
- Nếu không dùng Docker: Cần cài sẵn PostgreSQL 16 (Port 5432) và Redis 7 (Port 6379).

---

### 2.2 Bước 1: Thiết lập biến môi trường (`.env`)
Tại thư mục gốc của dự án, sao chép file cấu hình mẫu `.env.example` thành `.env`:

```bash
# Trên Windows PowerShell
Copy-Item .env.example .env

# Trên Linux/macOS
cp .env.example .env
```

#### Cách 1: Sử dụng Database Cloud Supabase dùng chung cả nhóm (KHUYÊN DÙNG)
Không cần cài Docker hay PostgreSQL trên máy, cả Frontend và Backend đều trỏ chung vào:
```env
# Database Supabase Cloud (Singapore Region)
DB_URL=jdbc:postgresql://db.oskyqehkztqksazhvmvn.supabase.co:5432/postgres?sslmode=require
DB_USERNAME=postgres
DB_PASSWORD=DBPGVEGEAI123

# Redis (Cache, Blacklist & Rate Limiting)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT Secret
JWT_SECRET=vegeai-super-secret-key-change-this-in-production-minimum-256-bits

# Cấu hình gửi mail OTP (Gmail SMTP)
# Nếu để trống: Hệ thống tự động in mã OTP ra Terminal/Console log để test nhanh!
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=

# Google Gemini AI Studio
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-3.8-flash
```

#### Cách 2: Sử dụng PostgreSQL & Redis Local qua Docker Compose
Nếu muốn chạy hoàn toàn offline trên máy cá nhân:
```env
DB_URL=jdbc:postgresql://localhost:5432/vegeai
DB_USERNAME=postgres
DB_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
```

---

### 2.3 Bước 2: Tự động khởi tạo dữ liệu mẫu (Auto Data Initializer)
Backend đã tích hợp 2 bộ khởi tạo dữ liệu tự động (`CommandLineRunner`):
1. **`UserDataInitializer.java`**: Tự động kiểm tra và khởi tạo 3 tài khoản mặc định (`admin@vegeai.com`, `member@vegeai.com`, `unverified@vegeai.com`) với pre-hashed SHA-256 + BCrypt encoding.
2. **`RecipeDataInitializer.java`**: Tự động nạp bộ từ điển Thẻ tag ẩm thực đa tầng và danh mục công thức mẫu chuẩn.

*Lưu ý về gửi mail OTP:* Khi chạy ở môi trường DEV mà chưa có Gmail App Password, hệ thống sẽ tự động in mã OTP nổi bật ra màn hình Terminal:
```text
===============================================================================
>>> [OTP DEV FALLBACK] REGISTRATION OTP FOR [email]: [XXXXXX] <<<
===============================================================================
```

---

### 2.4 Bước 3: Khởi động Database & Redis qua Docker Compose (Nếu dùng Local)
Nếu chọn chạy Docker local:
```bash
docker-compose up -d postgres redis
```

### 2.4 Bước 3: Chạy ứng dụng Spring Boot
Dự án có thể chạy trực tiếp từ dòng lệnh hoặc mở bằng IntelliJ IDEA / VS Code:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

- Cổng mặc định: `8081` (Cấu hình tại `server.port=8081`).
- OpenAPI / Swagger UI: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- OpenAPI JSON Spec: [http://localhost:8081/api-docs](http://localhost:8081/api-docs)

---

### 2.5 Bước 4: Chạy kiểm thử tự động (Run Test Suite)
Để đảm bảo tất cả các luồng hoạt động ổn định và không bị hồi quy:

```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```
*Kết quả kỳ vọng: `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS`.*

---

## 3. MA TRẬN API ĐÃ HOÀN THÀNH (API REFERENCE & SECURITY)

Tất cả các API tuân theo cấu trúc phản hồi chuẩn [`ApiResponse<T>`](src/main/java/com/vegeai/backend/common/dto/ApiResponse.java):
```json
{
  "success": true,
  "code": 200,
  "message": "Thông báo kết quả",
  "data": { ... },
  "timestamp": "2026-10-08T10:00:00Z"
}
```

---

### A. Phân hệ Authentication (`/api/auth`)
*Toàn bộ các endpoint này là PUBLIC (không cần Bearer Token).*

| Phương thức | Endpoint | Request Body | Mô tả nghiệp vụ & Cơ chế bảo mật |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | `RegisterRequest` | Đăng ký thành viên mới (`PENDING_VERIFICATION`). Sinh OTP 6 số ngẫu nhiên có hiệu lực 3 phút và gửi qua email. Mật khẩu phải được pre-hash SHA-256 từ client. |
| `POST` | `/api/auth/verify-email` | `VerifyEmailRequest` | Xác thực OTP 6 số để kích hoạt tài khoản sang `ACTIVE`. |
| `POST` | `/api/auth/resend-otp` | `ResendOtpRequest` | Gửi lại OTP kích hoạt. **Áp dụng Cooldown 30 giây** chống spam email. |
| `POST` | `/api/auth/login` | `LoginRequest` | Đăng nhập tài khoản. Có **chống tấn công vét cạn (Brute-force protection)**: Quá 5 lần sai tài khoản hoặc 20 lần sai IP trong 15 phút sẽ bị khóa tạm thời. Trả về Access Token (JSON) và tự động set Cookie `refreshToken` (`HttpOnly`, `SameSite=Strict`). |
| `POST` | `/api/auth/refresh` | *(Header hoặc Cookie)* | Xoay vòng Access Token mới bằng `refreshToken` hợp lệ mà không bắt người dùng đăng nhập lại. |
| `POST` | `/api/auth/logout` | *(Bearer + RefreshToken)* | Đăng xuất với cơ chế **Dual Invalidation**: Blacklist Access Token vào Redis với thời gian sống còn lại + Đánh dấu thu hồi Refresh Token trong DB + Xóa Cookie. |
| `POST` | `/api/auth/forgot-password` | `ForgotPasswordRequest` | Yêu cầu OTP đặt lại mật khẩu. Áp dụng cơ chế **Anti-Enumeration** (luôn trả về thông báo chung để hacker không thể dò quét danh sách email tồn tại trên hệ thống). |
| `POST` | `/api/auth/reset-password` | `ResetPasswordRequest` | Xác thực OTP và lưu mật khẩu mới mã hóa BCrypt. Tự động thu hồi mọi phiên đăng nhập cũ trên các thiết bị khác. |

---

### B. Phân hệ User Profile & Health (`/api/users`)
*Yêu cầu Header: `Authorization: Bearer <accessToken>`.*

| Phương thức | Endpoint | Request Body | Mô tả nghiệp vụ & Quy tắc tính toán |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/users/me` | *None* | Xem thông tin hồ sơ của chính mình (thông tin cá nhân, chiều cao, cân nặng, BMI, dị ứng, mục tiêu calo hàng ngày). Không bao giờ lộ `passwordHash`. |
| `PUT` | `/api/users/me` | `UpdateUserProfileRequest` | Cập nhật thông tin cá nhân và chế độ ăn.<br/>**Quy tắc BMI tất định (Deterministic Logic):** Khi `heightCm` hoặc `weightKg` thay đổi, hệ thống backend tự động tính toán lại: $$\text{BMI} = \frac{\text{Cân nặng (kg)}}{(\text{Chiều cao (m)})^2}$$ làm tròn 2 chữ số thập phân. Không bao giờ để AI bịa ra chỉ số này! |
| `PUT` | `/api/users/me/password` | `ChangePasswordRequest` | Đổi mật khẩu: Xác thực mật khẩu cũ, kiểm tra không trùng mật khẩu cũ, cập nhật BCrypt và **thu hồi toàn bộ Refresh Tokens** của tài khoản đó. |
| `DELETE` | `/api/users/me` | *None* | Tự vô hiệu hóa tài khoản (`DISABLED`) và đăng xuất toàn bộ phiên. |
| `GET` | `/api/users/{userId}/public` | *None* | Lấy thông tin công khai an toàn của một User (dùng để hiển thị tác giả công thức nấu ăn, bình luận). **Tuyệt đối ẩn giấu:** Email, SĐT, Địa chỉ, Số đo chiều cao/cân nặng và dị ứng. |

---

### C. Phân hệ Super Admin Control Center (`/api/admin`)
*Chỉ dành riêng cho tài khoản có quyền `ROLE_ADMIN` (Kiểm tra bởi `@PreAuthorize("hasRole('ADMIN')")`).*

| Phương thức | Endpoint | Request / Params | Mô tả nghiệp vụ & Cơ chế bảo vệ |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/users` | `page`, `size`, `search`, `role`, `status`, `sortBy`, `direction` | Danh sách người dùng phân trang động bằng `Specification`. Hỗ trợ tìm kiếm từ khóa tương ứng với `username`, `email`, `fullName` và lọc theo `role`, `status`. Trả về [`PageResponse<UserProfileResponse>`](src/main/java/com/vegeai/backend/common/dto/PageResponse.java). |
| `GET` | `/api/admin/users/{userId}` | `userId` (Path) | Xem chi tiết toàn bộ hồ sơ của người dùng theo UUID. |
| `PATCH` | `/api/admin/users/{userId}/status` | `UpdateUserStatusRequest` | Thay đổi trạng thái tài khoản (`ACTIVE`, `LOCKED`, `DISABLED`).<br/>🛡️ **Admin Immunity Rule:** Ngăn chặn Admin khóa tài khoản của Admin khác hoặc tự khóa chính mình. Khi tài khoản bị khóa/hủy, toàn bộ Refresh Token của user đó bị thu hồi ngay lập tức. |
| `PATCH` | `/api/admin/users/{userId}/role` | `UpdateUserRoleRequest` | Nâng cấp quyền lên `ADMIN` hoặc hạ xuống `MEMBER`. Ngăn chặn tự hạ quyền chính mình. |
| `GET` | `/api/admin/dashboard/stats` | *None* | Thống kê số liệu hệ thống tổng quan: Tổng User, Số tài khoản Active, Pending, Locked, Disabled, Số lượng Member/Admin, Lượng user đăng ký mới hôm nay & 7 ngày gần nhất. |
| `GET` | `/api/admin/ai/status` | *None* | **Giám sát AI Ops (FR-ADMIN-03):** Đo độ trễ (latency), trạng thái hoạt động (uptime), hạn ngạch quota của các mô hình LLM ngoài (Gemini 1.5 Flash, Whisper STT) và bộ kiểm duyệt an toàn cục bộ. |
| `GET` | `/api/admin/ai/audit-logs` | *None* | Nhật ký kiểm toán AI (Audit Trail Logs): Lưu vết tóm tắt prompt, mô hình thực thi, độ trễ xử lý và các cờ cảnh báo kiểm duyệt vi phạm. |

---

## 4. HƯỚNG DẪN & ĐẶC TẢ TÍCH HỢP AI CHATBOT PROTOTYPE (`/api/ai/chat`)

### 4.1 Kiến trúc luồng hoạt động
```
[Client (Web React / Vue / Mobile App / Figma Prototype)]
      │
      ├──> Người dùng VÃNG LAI (GUEST - Không đăng nhập): Header không có Bearer token
      │     └──> GuestAiChatRateLimitFilter: Giới hạn tối đa 3 câu/24h theo IP client
      │           └──> Nếu vượt quá 3 câu -> Trả về HTTP 429 (ErrorCode 1402: GUEST_AI_QUOTA_EXCEEDED)
      │                 └──> Frontend hiển thị Modal bắt buộc Đăng ký / Đăng nhập
      │
      └──> Thành viên ĐÃ ĐĂNG NHẬP (MEMBER / ADMIN): Header Bearer <AccessToken>
            └──> Bỏ qua bộ lọc giới hạn -> Trò chuyện KHÔNG GIỚI HẠN
                  │
                  ▼
         AiChatbotController
                  │
                  ▼
         Structured Prompting & Guardrails:
           - Dị ứng (Allergies) là RÀNG BUỘC CỨNG (Hard Constraint)
           - Chế độ ăn chay (Vegan / Lacto-Ovo) không bị vi phạm
           - Kèm Disclaimer y tế bắt buộc ở cuối câu trả lời
```

---

### 4.2 Đặc tả giao tiếp API Chatbot

- **URL:** `POST http://localhost:8081/api/ai/chat`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer <accessToken>` *(Tùy chọn: Nếu không truyền, hệ thống tự động tính là Guest Trial theo IP)*
- **Request Body:**
```json
{
  "message": "Tôi muốn gợi ý thực đơn bữa tối giàu đạm không dùng đậu phụ?",
  "sessionId": "4f55bb89-53e3-4712-a7d0-128c688d0b28"
}
```
*(Ghi chú: `sessionId` là tùy chọn. Nếu client không truyền, backend tự sinh UUID mới và trả về).*

- **Response thành công (HTTP 200 OK):**
```json
{
  "success": true,
  "code": 200,
  "message": "AI response generated successfully.",
  "data": {
    "sessionId": "4f55bb89-53e3-4712-a7d0-128c688d0b28",
    "reply": "Chào bạn! Dưới đây là tư vấn dinh dưỡng chay cho bạn: Hãy đảm bảo bổ sung đủ Protein từ các nguồn đậu nành, hạt sen, nấm và vitamin B12. (Lưu ý: Lời khuyên này chỉ mang tính tham khảo dinh dưỡng, không thay thế chỉ định y khoa).",
    "recommendedRecipes": [
      "Đậu phụ sốt cà chua nấm đông cô",
      "Salad bơ rong biển mè rang",
      "Canh rong biển đậu hũ non"
    ],
    "isGuestTrial": false,
    "timestamp": "2026-10-08T10:05:00Z"
  }
}
```

- **Response khi Khách vãng lai dùng hết 3 lượt thử (HTTP 429 Too Many Requests):**
```json
{
  "success": false,
  "code": 1402,
  "message": "Guest trial limit reached (3 requests per 24h). Please register or log in to continue.",
  "timestamp": "2026-10-08T10:05:00Z"
}
```

---

### 4.3 Mã nguồn mẫu Frontend (TypeScript / React)

Tạo file `useAiChat.ts` trong frontend của bạn:

```typescript
export interface AiChatResponse {
  sessionId: string;
  reply: string;
  recommendedRecipes: string[];
  isGuestTrial: boolean;
  timestamp: string;
}

export async function sendChatMessage(
  message: string,
  sessionId?: string,
  onQuotaExceeded?: () => void
): Promise<AiChatResponse | null> {
  const token = localStorage.getItem("accessToken");

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch("http://localhost:8081/api/ai/chat", {
    method: "POST",
    headers,
    body: JSON.stringify({ message, sessionId }),
  });

  const result = await response.json();

  // Bắt trường hợp khách vãng lai đã dùng hết 3 câu hỏi
  if (response.status === 429 && result.code === 1402) {
    if (onQuotaExceeded) {
      onQuotaExceeded(); // Kích hoạt Popup Modal: "Hãy đăng ký tài khoản để tiếp tục chat không giới hạn!"
    }
    return null;
  }

  if (!result.success) {
    throw new Error(result.message || "Lỗi giao tiếp với AI");
  }

  return result.data;
}
```

---

### 4.4 Hướng dẫn nâng cấp từ Prototype Mock sang Real LLM (Google Gemini 1.5)

Hiện tại [`AiChatbotController`](src/main/java/com/vegeai/backend/modules/ai/controller/AiChatbotController.java) trả về cấu trúc dữ liệu chuẩn hóa (Deterministic Mock) giúp Frontend và Figma test luồng hoàn hảo mà không tốn chi phí gọi API. Khi bạn sẵn sàng tích hợp Google Gemini thật:

1. **Lấy API Key:** Truy cập [Google AI Studio](https://aistudio.google.com/) tạo miễn phí 1 API Key.
2. **Khai báo biến môi trường:** Thêm vào `.env`:
   ```env
   GEMINI_API_KEY=AIzaSy...
   GEMINI_MODEL=gemini-1.5-flash
   ```
3. **Khai báo System Instruction chuẩn theo Master Rule:**
   > *"Bạn là CrocVeggie, chuyên gia dinh dưỡng thực vật thông minh của VEGEAI. Quy tắc bất biến: Dị ứng của người dùng là ràng buộc cứng tuyệt đối. Không bao giờ tự ý chẩn đoán y tế. Luôn trả lời bằng tiếng Việt thân thiện và đính kèm danh sách món ăn gợi ý dưới dạng JSON."*
4. **Cấu hình Spring Service:** Tạo `GeminiClientService` sử dụng `RestClient` của Spring Boot 3 để gọi endpoint:  
   `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${GEMINI_API_KEY}`.

---

## 5. TÀI KHOẢN KIỂM THỬ MẶC ĐỊNH (TEST CREDENTIALS)

Khi khởi động cơ sở dữ liệu qua Docker Compose, script `db-init.sql` đã khởi tạo sẵn tài khoản quản trị viên:

- **1. Tài khoản Super Admin:**
  - **Email:** `admin@vegeai.com`
  - **Username:** `admin`
  - **Mật khẩu thô:** `Admin@123`
  - **Chuỗi Pre-hash SHA-256 (truyền trong API request):**  
    `e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7`
  - **Role:** `ADMIN` | **Trạng thái:** `ACTIVE`

- **2. Tài khoản Thành viên Active (Member):**
  - **Email:** `member@vegeai.com`
  - **Username:** `member`
  - **Mật khẩu thô:** `Member@123`
  - **Chuỗi Pre-hash SHA-256:**  
    `abe2d3ed5419e1a2293c034a6b375a622ff5a60e5ac30f29c461220898ffdd97`
  - **Role:** `MEMBER` | **Trạng thái:** `ACTIVE`

- **3. Tài khoản Chưa kích hoạt (Unverified Member - Dùng test OTP):**
  - **Email:** `unverified@vegeai.com`
  - **Username:** `unverified`
  - **Mật khẩu thô:** `User@123`
  - **Chuỗi Pre-hash SHA-256:**  
    `3e7c19576488862816f13b512cacf3e4ba97dd97243ea0bd6a2ad1642d86ba72`
  - **Role:** `MEMBER` | **Trạng thái:** `PENDING_VERIFICATION`

---

## 6. LỘ TRÌNH PHÁT TRIỂN TIẾP THEO (NEXT SPRINTS ROADMAP)

Dành cho lập trình viên Backend tiếp tục phát triển:

### 🥗 Sprint 3: Phân hệ Danh mục Nguyên liệu & Công thức Nấu ăn (`/api/recipes`)
- **Entities cần tạo:** `Recipe`, `Ingredient`, `RecipeIngredient`, `Category`.
- **Nghiệp vụ:**
  - CRUD món ăn chay (phân loại rõ nhãn `VEGAN`, `LACTO_OVO`, `LACTO`, v.v.).
  - Bảng nguyên liệu chuẩn tích hợp hàm lượng Kcal, Protein, Carb, Fat trên 100g.
  - Công thức tính calo món ăn = Tổng calo thành phần (tất định từ DB).

### 📅 Sprint 4: Phân hệ Lập Kế Hoạch Bữa Ăn 7 Ngày (`/api/meal-plans`)
- **Entities cần tạo:** `MealPlan`, `MealPlanDay`, `MealPlanItem`.
- **Nghiệp vụ:**
  - Xếp lịch ăn theo ngày (Sáng, Trưa, Tối, Snack).
  - So khớp dị ứng của User với thành phần món ăn: **Nếu phát hiện dị ứng, từ chối lưu và bắn cảnh báo ngay lập tức**.
  - Tính tổng calo thực tế mỗi ngày và so sánh với `dailyCalorieTarget` của User.

### 🧊 Sprint 5: Phân hệ Quét Tủ Lạnh & Vision AI (`/api/fridge`)
- **Entities:** `PantryItem`, `FridgeScan`, `DetectedIngredient`.
- **Nghiệp vụ:** Upload ảnh tủ lạnh $\rightarrow$ AI nhận diện danh sách nguyên liệu $\rightarrow$ Người dùng xác nhận $\rightarrow$ Lưu vào kho nguyên liệu cá nhân.

---

## 7. CÁC NGUYÊN TẮC CỐT LÕI CẦN TUÂN THỦ (CORE MASTER RULES)

Theo quy định kiến trúc trong [`GEMINI.md`](GEMINI.md), mọi đóng góp mã nguồn mới bắt buộc tuân theo:

1. **Deterministic Logic First:** Các phép tính toán học (BMI, BMR, Calo, macro dinh dưỡng) bắt buộc phải do code Java tính toán chính xác. AI chỉ đóng vai trò giải thích hoặc gợi ý bằng văn bản, **không được để AI tự tính hoặc ghi đè số liệu dinh dưỡng**.
2. **Allergies & Diets là Ràng buộc Cứng (Hard Constraints):** Không bao giờ gợi ý hoặc cho phép thêm món ăn chứa thành phần nằm trong danh sách dị ứng của người dùng.
3. **Sử dụng DTO & Validation:** Không bao giờ truyền Entity trực tiếp ra ngoài Controller; luôn dùng DTO và validate bằng `@Valid`.
4. **Không xóa code cũ:** Khi mở rộng tính năng, không làm đứt gãy hoặc xóa bỏ các hàm đang chạy ổn định của các phân hệ trước.
5. **Chạy test trước khi commit:** Luôn chạy `./mvnw test` để đảm bảo 100% test cases đều pass trước khi tạo Pull Request.

---
*Chúc bạn có trải nghiệm lập trình tuyệt vời cùng VEGEAI Backend! Nếu có bất kỳ thắc mắc nào, hãy xem tài liệu kiến trúc tại [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) hoặc file [GEMINI.md](GEMINI.md).*
