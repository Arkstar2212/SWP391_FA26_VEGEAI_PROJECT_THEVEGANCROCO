# 🏃‍♂️ VEGEAI — TÀI LIỆU BÀN GIAO PHÂN HỆ GOOGLE FITNESS TRACKING V3

> **Dự án:** SWP391 - VEGEAI (Nền tảng Hỗ trợ Dinh dưỡng & Lối sống Ăn Chay Thông Minh)  
> **Phiên bản:** Google Fitness Tracking V3 (Omni-Wearable Health Hub & Zen Activity Rings)  
> **Tên nhánh chính thức:** `google-fitness-tracking-v3`  
> **Trạng thái:** ✅ Đã hoàn thiện toàn diện, kiểm thử build Vite & Spring Boot compile 100% thành công, không xung đột.

---

## 📌 1. THÔNG TIN NHÁNH LÀM VIỆC & LỆNH PULL / SYNC

Cả hai repository **FRONTEND** và **BACKEND** đều được đồng bộ lên nhánh:

$$\mathbf{google-fitness-tracking-v3}$$

### 🔹 Hướng dẫn Git cho Developer tiếp nhận:

#### 1. Repository Frontend (`SWP391_VEGEAI_FRONTEND`):
```bash
cd FRONTEND
git fetch origin
git checkout google-fitness-tracking-v3
git pull origin google-fitness-tracking-v3
npm install
npm run dev
# Mở trình duyệt tại: http://localhost:5173 hoặc http://localhost:5174
```

#### 2. Repository Backend (`SWP391_FA26_VEGEAI_PROJECT_THEVEGANCROCO`):
```bash
cd BACKEND
git fetch origin
git checkout google-fitness-tracking-v3
git pull origin google-fitness-tracking-v3
./mvnw clean compile
./mvnw spring-boot:run
# Server backend chạy tại: http://localhost:8081
```

---

## 🌟 2. CÁC TÍNH NĂNG ĐÃ NÂNG CẤP VÀ HOÀN THIỆN TRONG V3

### 1. Đồng Bộ Google Fit Chuẩn Google Identity Services (GIS) — Khắc Phục Triệt Để Lỗi Redirect URI
- **Nguyên nhân lỗi cũ**: Trước đây cơ chế OAuth sử dụng `window.open` với Redirect URI thủ công dẫn đến lỗi `redirect_uri_mismatch` khi chạy trên localhost hoặc domain thay đổi cổng.
- **Giải pháp V3**:
  - Tích hợp chuẩn **Google Identity Services (GIS) Token Client** (`google.accounts.oauth2.initTokenClient`).
  - Xác thực popup in-place thông qua kênh trao đổi thông điệp an toàn `postMessage`, không phụ thuộc Redirect URI callback URL.
  - Tự động fallback sang popup chuẩn nếu SDK đang khởi tạo.
  - Phân quyền Scope an toàn: `https://www.googleapis.com/auth/fitness.activity.read` và `https://www.googleapis.com/auth/fitness.body.read`.
  - Hỗ trợ giải nén và trích xuất dữ liệu đa nguồn từ Google Fitness REST API v1 (`dataset:aggregate`):
    - Bước chân (`com.google.step_count.delta`)
    - Calo năng lượng tiêu hao (`com.google.calories.expended`)
    - Nhịp tim trung bình (`com.google.heart_rate.bpm`)
    - Thiết bị nguồn (`Xiaomi Mi Fitness`, `Samsung Health Connect`, `Apple Watch`, `Pixel Watch`).

### 2. Bộ Mô Phỏng Vận Động (Simulator Presets) & Chế Độ Làm Trắng (Reset Empty State)
- Để phục vụ kiểm thử, demo đồ án và hỗ trợ người dùng chưa có đồng hồ thông minh:
  - 3 Kịch bản mô phỏng thực tế:
    - **Nhẹ nhàng**: 3.850 bước, +145 kcal, vận động 24 phút, nhịp tim 68 bpm.
    - **Vừa phải**: 8.420 bước, +380 kcal, vận động 52 phút, nhịp tim 74 bpm.
    - **Cường độ cao**: 13.600 bước, +620 kcal, vận động 85 phút, nhịp tim 82 bpm.
  - Nút **"Xóa mô phỏng"**: Cho phép đưa toàn bộ chỉ số về trạng thái sạch ban đầu (`—`), loại bỏ hoàn toàn các giá trị hardcode rác.

### 3. Vòng Tròn Hoạt Động Zen Rings & Cân Bằng Năng Lượng Real-time
- **Giao diện SVG concentric rings cao cấp**:
  - Vòng 1 (Đỏ/Cam): Calo nạp vào vs Calo tiêu hao thực tế.
  - Vòng 2 (Xanh lục): Đạm thực vật (Protein).
  - Vòng 3 (Cyan/Lam): Chất xơ tự nhiên (Fiber).
- **Tính toán chuẩn y khoa**:
  - BMR & TDEE tính toán dựa trên phương trình Mifflin-St Jeor & Harris-Benedict chuẩn xác từ chiều cao, cân nặng, độ tuổi, giới tính của người dùng.
  - Tự động tính toán cân bằng năng lượng: $\text{Net Balance} = \text{Calo nạp} - (\text{BMR} + \text{Calo vận động Google Fit})$.
  - Cảnh báo trực quan khi calo thâm hụt (Deficit) hoặc thặng dư (Surplus).
  - Tự động cảnh báo mâu thuẫn giữa chế độ ăn chay (Ovo-lacto, Ovo, Lacto) và danh sách dị ứng thực phẩm (Trứng, Sữa).

### 4. Đăng Nhập Nhanh Bằng Tài Khoản Google (Google Sign-In)
- Render nút Google Sign-In chính thức thông qua Google Identity Services tại form đăng nhập (`LoginForm.tsx`).
- Tự động xóa `g_state` cookie cooldown để ngăn Google One Tap chặn hiển thị.
- Backend cung cấp endpoint `POST /api/v1/auth/google` tiếp nhận ID Token từ Google, giải mã, xác thực và cấp phát JWT token an toàn.

### 5. Tích Hợp Sâu AI Dinh Dưỡng (Gemini AI Engine Context Injection)
- Nhúng trực tiếp widget Lịch ăn thông minh và Vòng Zen Rings vào giao diện AI Chat (`ChatInteractiveWidgets.tsx`, `AiChatPage.tsx`).
- Bơm ngữ cảnh nguyên liệu thực tế đang có trong kho Tủ lạnh người dùng (`activeFridgeItems`) và mục tiêu calo cá nhân hóa vào hệ thống Gemini AI:
  - AI chỉ gợi ý món chay nấu được từ những nguyên liệu thực tế có sẵn.
  - AI tự động điều chỉnh bữa phụ hồi phục năng lượng dựa trên lượng calo tiêu hao mà Google Fit đã ghi nhận trong ngày.

---

## 📁 3. DANH SÁCH CÁC FILE ĐƯỢC CHỈNH SỬA VÀ BỔ SUNG

### 🔹 Phân hệ Frontend:
1. `src/utils/wearableService.ts`: Lõi xử lý tích hợp Google Fit REST API, GIS OAuth2 Token Client, quản lý dữ liệu vận động và presets.
2. `src/components/WearableGoogleFitTracker.tsx`: Giao diện Omni-Wearable Health Hub, đồng bộ bước chân, calo, nhịp tim, giấc ngủ, nút chọn kịch bản mô phỏng và reset.
3. `src/components/HealthActivityRings.tsx`: Vòng tròn vận động Zen Activity Rings đồng tâm SVG, tính tỷ lệ phần trăm calo, protein, fiber theo thời gian thực.
4. `src/components/ChatInteractiveWidgets.tsx`: Tiện ích nhúng tương tác lịch ăn và vòng sức khỏe ngay trong khung trò chuyện AI Chat.
5. `src/pages/AccountSettingsPage.tsx`: Tối ưu hóa tab Hồ sơ & Chỉ số sức khỏe, tích hợp đồng bộ dữ liệu vòng hoạt động, BMR, TDEE và cảnh báo dị ứng.
6. `src/pages/AiChatPage.tsx`: Kết nối dữ liệu wearable với AI Assistant để đưa ra lời khuyên dinh dưỡng theo calo tiêu hao thực tế.
7. `src/features/auth/components/LoginForm.tsx`: Nút đăng nhập Google chính thức và xử lý One Tap token exchange.
8. `src/services/apiClient.ts`: Bổ sung API client call cho xác thực Google OAuth.

### 🔹 Phân hệ Backend:
1. `src/main/java/com/vegeai/backend/modules/ai/service/GeminiAiService.java`: Nâng cấp prompt ngữ cảnh AI với nguyên liệu kho tủ lạnh và chỉ số dinh dưỡng người dùng.
2. `src/main/java/com/vegeai/backend/modules/auth/controller/AuthController.java`: Endpoint `POST /api/v1/auth/google` hỗ trợ đăng nhập qua Google Identity.

---

## 🧪 4. HƯỚNG DẪN KIỂM THỬ NHANH (VERIFICATION CHECKS)

| STT | Kịch bản kiểm thử | Kết quả mong đợi |
|:---:|:---|:---|
| 1 | Mở trang Hồ sơ (`/account?tab=profile`) khi chưa kết nối Google Fit | Hiển thị trạng thái sạch `—`, không có số liệu ảo. Các nút chọn kịch bản mô phỏng hiển thị sẵn sàng. |
| 2 | Bấm chọn kịch bản "Vừa phải (8.420 bước)" | Vòng tròn Zen Rings cập nhật ngay lập tức calo tiêu hao (+380 kcal), nhịp tim 74 bpm, tính toán lại Net Calorie Balance. |
| 3 | Bấm "Xóa mô phỏng" | Toàn bộ dữ liệu vận động quay về trạng thái ban đầu sạch sẽ. |
| 4 | Bấm "Đồng bộ Google Fit" | Cửa sổ popup Google OAuth xuất hiện, yêu cầu quyền đọc dữ liệu vận động mà không bị lỗi `redirect_uri_mismatch`. |
| 5 | Mở AI Chat (`/ai-chat`) và hỏi về thực đơn phục hồi sau khi vận động | AI nhận diện lượng calo và bước chân đã đồng bộ, đưa ra gợi ý món phụ giàu protein (sữa đậu nành, sinh tố bơ chuối hạt sen). |
| 6 | Đăng nhập bằng Google tại `/login` | Nút Google hiển thị chuẩn Google Design, bấm vào mở bảng chọn tài khoản Google để đăng nhập vào hệ thống. |

---

## 🎯 5. KHUYẾN NGHỊ BƯỚC TIẾP THEO CHO DEV TIẾP NHẬN

1. Đăng ký Production OAuth Consent Screen trên Google Cloud Console để mở rộng phạm vi người dùng không giới hạn danh sách Test Users.
2. Tích hợp thêm Webhook thông báo đẩy (Push Notification) nhắc nhở người dùng uống nước và vận động khi chỉ số bước chân dưới 3.000 bước vào buổi chiều.
3. Liên kết thêm Health Connect API khi đóng gói ứng dụng di động Hybrid (Capacitor / React Native).
