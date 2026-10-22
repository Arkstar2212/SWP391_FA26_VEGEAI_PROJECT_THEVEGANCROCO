# 🚀 VEGEAI — TÀI LIỆU BÀN GIAO DỰ ÁN CHO DEV TÀI (BACKEND & FULL-STACK)

> **Tài liệu bàn giao kiến trúc, chức năng mới và hướng dẫn thiết lập cho Dev Tài.**  
> **Dự án:** SWP391 - VEGEAI (Nền tảng Hỗ trợ Dinh dưỡng & Mua sắm Chay Thông minh)  
> **Tên nhánh thống nhất (Frontend & Backend):** `feature/vegeai-redesign-smart-market-and-ai`  
> **Trạng thái:** Toàn bộ chức năng đã được hoàn thiện, kiểm thử tự động `npm run build` và backend test suite pass, không xung đột.

---

## 📌 1. THÔNG TIN NHÁNH LÀM VIỆC & LỆNH PULL (QUAN TRỌNG)

Để đảm bảo đồng bộ tuyệt đối giữa Frontend và Backend, **cả hai repository đều sử dụng chung một tên nhánh duy nhất**:

$$\text{Tên nhánh: } \mathbf{feature/vegeai-redesign-smart-market-and-ai}$$

### Lệnh Git cho Dev Tài:

#### 🔹 Với Backend (`SWP391_FA26_VEGEAI_PROJECT_THEVEGANCROCO`):
```bash
cd BACKEND
git fetch origin
git checkout feature/vegeai-redesign-smart-market-and-ai
git pull origin feature/vegeai-redesign-smart-market-and-ai
./mvnw clean compile
./mvnw spring-boot:run
```

#### 🔹 Với Frontend (`SWP391_VEGEAI_FRONTEND`):
```bash
cd FRONTEND
git fetch origin
git checkout feature/vegeai-redesign-smart-market-and-ai
git pull origin feature/vegeai-redesign-smart-market-and-ai
npm install
npm run dev
```

---

## 🌟 2. CÁC TÍNH NĂNG MỚI ĐÃ HOÀN TẤT & ĐIỂM CHÚ Ý KỸ THUẬT

### 1. Phân hệ Quét Tủ Lạnh (AI Vision Scan) & Đánh giá độ tin cậy (`FridgeScanPage.tsx`)
- **Khắc phục lỗi False Positive Dấu chấm than đỏ (!)**:
  - Trước đây hàm `isNonFoodKeyword` kiểm tra chuỗi con chứa `'go'` và `'da'`, khiến "Cải ngọt" (`n-g-o-t`) và "Đậu phụ" (`da-u`) bị nhận diện nhầm là vật phẩm không ăn được.
  - Đã chuyển sang bộ đối sánh cụm từ ngữ cảnh nhiều âm tiết: `cui kho`, `que cui`, `khuc go`, `canh cay`, `dong la`, `la kho`, `soi da`, `rac thai`...
- **Hàm `evaluateImageReliability`**:
  - Ảnh hợp lệ (chứa rau củ quả, nấm, đậu hũ) $\rightarrow$ Huy hiệu xanh: `✅ Ảnh đạt chuẩn: N nguyên liệu`.
  - Ảnh lỗi (cành cây, đống lá, khúc củi) $\rightarrow$ Huy hiệu đỏ: `❌ Không đáng tin cậy: [Lý do]`.
- **Cơ chế loại bỏ ảnh lỗi linh hoạt**:
  - Nút `×` trên từng thumbnail để xóa ảnh đơn lẻ.
  - Banner cảnh báo ảnh lỗi với nút 1-chạm: `✨ Tự động loại bỏ tất cả ảnh lỗi` $\rightarrow$ Tự động dọn sạch các ảnh cành cây/đống lá và tính toán lại danh sách nguyên liệu từ các ảnh đạt chuẩn.
- **Tích hợp liên kết chéo**:
  - Lưu vào kho tủ lạnh người dùng (`/fridge`).
  - Chuyển nguyên liệu sang AI Bếp Trưởng (`/ai-chef`).
  - Lên thực đơn tuần (`/meal-plans`).
  - Tạo nhanh bài viết Blog chia sẻ công thức món mới (`/blog/write`).

---

### 2. Phân hệ Đi Chợ Thông Minh (Smart Market) — Bách Hóa Xanh & ShopeeFood (`SmartMarketPage.tsx`)
- **Định vị GPS thực tế**:
  - Tích hợp Geolocation API định vị tọa độ vệ tinh và Geocoding ngược OpenStreetMap (Nominatim).
  - Lưu cache vị trí vào `sessionStorage` (`vegeai:session:gps_coords`, `vegeai:session:gps_address`) $\rightarrow$ Không hỏi lại quyền định vị trong cùng phiên làm việc.
  - Tìm kiếm và neo các quán chay, siêu thị thực tế gần vị trí người dùng.
- **Mô phỏng Mua sắm Bách Hóa Xanh Online**:
  - **Banner Khuyến Mãi VietGAP**: "Rau Lá Quốc Dân Đồng Giá 5.000đ/gói" (Cải ngọt, Cải thìa, Mồng tơi, Rau muống, Rau dền đỏ) đi kèm nút bấm thêm nhanh 5k.
  - **"Hôm Nay Nấu Gì? — Mua Trọn Combo Nguyên Liệu Theo Món"**:
    - 4 Combo món chay đầy đủ: Canh Chua Nam Bộ, Đậu Hũ Sốt Cà Nấm Đông Cô, Lẩu Nấm Đại Bổ, Rau Muống Xào Tỏi Chay.
    - Nút `🛒 Mua combo`: 1-click thêm đầy đủ nguyên liệu vào giỏ hàng đa cửa hàng và danh sách mua sắm.
  - **Bộ lọc nhóm hàng Bách Hóa Xanh**: `Tất cả`, `🥬 Rau củ VietGAP`, `🍄 Nấm tươi`, `🌱 Đậu hũ & Đạm chay`, `🍚 Gạo & Đồ khô`, `🧂 Gia vị chay`.
- **Giỏ hàng đa cửa hàng (Multi-Store Cart) kiểu ShopeeFood**:
  - Cho phép người dùng chọn mua sản phẩm từ nhiều cửa hàng cùng một lúc.
  - Tự động gom nhóm đơn hàng theo từng cửa hàng (Tách biệt tiền hàng, phí ship, thời gian giao hàng).
  - Thanh toán gộp 1 lần.
  - Sau khi thanh toán thành công, tự động cập nhật nguyên liệu vừa mua vào Kho tủ lạnh người dùng.

---

### 3. Tổ chức lại "Hồ sơ & Chỉ số sức khỏe" và "Lịch biểu & Thực đơn" (`AccountSettingsPage.tsx`)
- Tách biệt hoàn toàn thành 4 tab quản lý qua tham số URL `?tab=`:
  - **`tab=profile` (Hồ sơ & Chỉ số sức khỏe)**: Thông tin cá nhân, thể trạng, dị ứng, tính toán BMI, BMR, TDEE, Cân nặng lý tưởng và Vòng tròn vận động hôm nay (`HealthActivityRings`).
  - **`tab=calendar` (Lịch biểu & Thực đơn)**: Kế hoạch calo cá nhân hóa, Lịch ăn 30 ngày, phân bổ 3 bữa chính kèm calo & protein, nút thêm nguyên liệu từng bữa vào giỏ đi chợ.
  - **`tab=security` (Bảo mật & Đăng nhập)**: Đổi mật khẩu, đặt lại mật khẩu bằng mã OTP email và giám sát phiên đăng nhập JWT.
  - **`tab=preferences` (Giao diện & Hệ thống)**: Tùy chọn giao diện Sáng / Tối và cam kết bảo vệ dữ liệu sức khỏe.
- Đồng bộ nhãn điều hướng ở `UserMenu.tsx` và `MainLayout.tsx`.

---

### 4. Giám Sát AI (AI Monitoring Dashboard) (`AdminAiMonitoringPage.tsx` & Backend Modules)
- Trang quản trị dành riêng cho Super Admin tại route `/admin/ai-monitoring`.
- Backend lưu trữ thực thể [`AiCallLog`](src/main/java/com/vegeai/backend/modules/ai/entity/AiCallLog.java) cho từng lượt gọi AI.
- Quản trị viên có thể theo dõi biểu đồ thời gian phản hồi (latency ms), tỷ lệ thành công (success rate), ước tính chi phí token, chế độ Fallback và bảng nhật ký chi tiết tất cả lượt gọi AI.

---

## 🛠️ 3. HƯỚNG DẪN KHỞI CHẠY HỆ THỐNG CHO DEV TÀI

### 1. Yêu cầu môi trường:
- **Node.js**: v18+ (khuyên dùng Node 20 hoặc 22 LTS).
- **Java**: JDK 21 LTS.
- **PostgreSQL**: v16+ (Port 5432, database `vegeai_db`).

### 2. Khởi chạy Backend:
```bash
cd BACKEND
# Cấu hình application.properties hoặc .env nếu mật khẩu postgres khác mặc định
./mvnw spring-boot:run
# Server backend sẽ chạy tại: http://localhost:8081
```

### 3. Khởi chạy Frontend:
```bash
cd FRONTEND
npm install
npm run dev
# Vite server chạy tại: http://localhost:5174 (hoặc 5173)
```

### 4. Tài khoản kiểm thử mặc định:
- **Super Admin**: `admin` / `Admin@123456` (truy cập `/admin` và `/admin/ai-monitoring`).
- **Thành viên**: `demouser` / `User@123456` (hoặc tự đăng ký tài khoản mới qua form đăng ký OTP).

---

## 🎯 4. CÔNG VIỆC GỢI Ý TIẾP THEO CHO DEV TÀI (NEXT STEPS)
1. Kết nối endpoint thanh toán trực tuyến chính thức (VNPAY / MoMo / ZaloPay) cho giỏ hàng đa cửa hàng ShopeeFood.
2. Tích hợp thêm webhook thông báo trạng thái đơn hàng thời gian thực qua WebSockets / SSE.
3. Bổ sung thêm các combo món chay vùng miền (Miền Bắc, Miền Trung) theo catalog mở rộng.
4. Kiểm thử giao diện trên thiết bị di động thực tế hoặc qua tunnel.
