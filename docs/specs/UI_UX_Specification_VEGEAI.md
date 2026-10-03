# TÀI LIỆU ĐẶC TẢ THIẾT KẾ GIAO DIỆN (UI/UX SPECIFICATION)
## DỰ ÁN: VEGEAI — NỀN TẢNG ĂN CHAY THÔNG MINH TÍCH HỢP TRÍ TUỆ NHÂN TẠO
### Linh vật thương hiệu: Cá Sấu Ăn Chay (**CrocVeggie**)
### Công nghệ giao diện: HTML5 / Tailwind CSS / Lucide Icons / React.js / Responsive Web

---

## 1. HỆ THỐNG NHẬN DIỆN THƯƠNG HIỆU & DESIGN SYSTEM

### 1.1. Triết lý Thiết kế (Design Principles)
- **Tươi mới & Xanh lành (Fresh & Vibrant):** Tôn vinh vẻ đẹp tự nhiên của rau củ quả, mang lại cảm giác thanh lọc, giàu năng lượng và tích cực.
- **Thân thiện & Gần gũi (Friendly & Accessible):** Phá vỡ cảm giác gò bó, khắt khe của việc ăn chay nhờ linh vật chú Cá Sấu Ăn Chay (**CrocVeggie**) vui nhộn, đáng yêu.
- **Minh bạch AI (AI Explainability & Trust):** Phân định rạch ròi giữa số liệu tính toán toán học xác định (BMI, BMR, TDEE, Calo) và các đề xuất sáng tạo của AI. Luôn hiển thị cảnh báo y tế (Medical Disclaimer).
- **Trực quan hóa dữ liệu (High-Density Visuals):** Bảng thực đơn tuần trực quan, thẻ trạng thái độ tươi (Freshness Badges), sơ đồ tương tác và bảng điều khiển Admin chuẩn SaaS hiện đại.

### 1.2. Bảng màu chủ đạo (Color Palette)
- **Màu chủ đạo (Primary - Emerald Leaf):**
  - `Emerald 600` (`#059669`) / `Emerald 500` (`#10B981`): Biểu tượng của lá xanh, mầm sống, sự thuần khiết thực vật.
  - `Emerald 50` (`#ECFDF5`) / `Mint 100` (`#D1FAE5`): Nền thẻ, khu vực nổi bật mềm mại.
- **Màu bổ trợ (Accent - Carrot & Avocado):**
  - `Carrot Orange` (`#F97316` / `#EA580C`): Màu cà rốt tươi, dùng cho nút Call-To-Action, điểm nhấn thị giác, cảnh báo mức độ tươi "Cần dùng ngay".
  - `Avocado Gold` (`#F59E0B` / `#D97706`): Màu ấm áp của quả bơ chín và năng lượng tích cực.
- **Màu trạng thái (Status Colors):**
  - `Fresh Green` (`#22C55E`): Nguyên liệu tươi ngon / AI Moderation an toàn.
  - `Warning Amber` (`#EAB308`): Cần nấu ngay trong 24h / AI Moderation nghi vấn.
  - `Danger Red` (`#EF4444`): Thực phẩm hỏng / Dị ứng nghiêm trọng / AI Moderation vi phạm cao $> 0.85$.
- **Màu nền & Phông chữ:**
  - `Background Light`: `#F8FAFC` (Slate 50) và `#FFFFFF` (Pure White).
  - `Text Dark`: `#0F172A` (Slate 900) cho tiêu đề chính, `#475569` (Slate 600) cho nội dung.
  - `Admin Dark Sidebar`: `#064E3B` (Deep Forest Green) kết hợp `#0F172A` (Slate 900).

### 1.3. Linh vật thương hiệu "CrocVeggie" trong giao diện
- **Vị trí xuất hiện:**
  - **Header Logo:** Biểu tượng huy hiệu tròn chú cá sấu xanh đội nón đầu bếp ôm bơ và cà rốt kèm chữ `VEGEAI - CrocVeggie`.
  - **Hero Section:** Hình minh họa lớn chào đón người dùng mới: *"Cá sấu còn mê rau xanh — Bạn đã sẵn sàng bắt đầu hành trình chay khỏe mạnh chưa?"*.
  - **Trạng thái rỗng (Empty States):** Cá sấu cầm giỏ trống khi người dùng chưa lưu món ăn nào.
  - **Hộp thoại Trợ lý Chatbot:** Avatar đại diện cho AI Nutritionist trong khung chat.
  - **Thông báo giới hạn (Trial Limit Modal):** Cá sấu cầm bảng đăng ký khi khách hết 3 câu hỏi dùng thử.

---

## 2. KIẾN TRÚC GIAO DIỆN PHÂN HỆ NGƯỜI DÙNG (USER PORTAL)

### Màn hình U-01: Trang Chủ & Tìm Kiếm Thông Minh (Public Discovery & Hero)
- **Top Navigation Bar:**
  - Logo `VEGEAI` tích hợp mascot CrocVeggie.
  - Menu điều hướng: *Khám phá, Thực đơn tuần (AI), Quét tủ lạnh (Vision), Trợ lý Chat, Quán chay quanh đây, Cộng đồng*.
  - Nút chuyển nhanh phân hệ: `[Chuyển sang Quản Trị Viên (Admin)]`.
  - Nút `Đăng nhập / Đăng ký` và Hồ sơ cá nhân.
- **Hero Section:**
  - Khẩu hiệu nổi bật: *"Nền tảng Ăn Chay Thông Minh — Lên thực đơn chuẩn AI, Quét tủ lạnh nấu ngay, Chăm sóc sức khỏe toàn diện"*.
  - Mascot CrocVeggie với nụ cười rạng rỡ.
  - Thanh tìm kiếm thông minh đa năng: Nhập tên món ăn ("Bún bò chay", "Cà ri nấm"), nguyên liệu ("Đậu hũ, nấm rơm") hoặc khu vực ("Quận 1, Cầu Giấy").
- **Dải chỉ số cộng đồng (Social Proof Counters):**
  - `15,000+` Công thức chay đã kiểm duyệt.
  - `98.6%` Thực đơn tuần chuẩn calo không vi phạm dị ứng.
  - `4.9/5` Đánh giá hài lòng từ cộng đồng chay Việt Nam.
- **Danh mục món ăn thịnh hành (Category Pills):** Thuần chay (Vegan), Chay dưỡng sinh, Bữa sáng nhanh 15 phút, Món lẩu chay sum vầy, Giàu protein thực vật.

### Màn hình U-02: Lập Thực Đơn Tuần Cá Nhân Hóa (AI Meal Planner Studio)
- **Khu vực thiết lập tham số (Health & Preferences Bar):**
  - Hiển thị chỉ số cơ thể: `BMI 22.5 (Bình thường)` | `TDEE Mục tiêu: 2,000 kcal/ngày`.
  - Thẻ chọn dị ứng (Allergy Tags): `[x] Đậu phộng` | `[x] Gluten` | `[+] Thêm dị ứng`.
  - Số lượng bữa: 3 bữa chính + 1 bữa phụ xế chiều.
  - Nút bấm chính: **"Tự Động Sinh Thực Đơn 7 Ngày Bằng AI"** (Kèm spinner hiệu ứng tải).
- **Bảng lịch thực đơn 7 ngày trực quan (Interactive Weekly Grid):**
  - 7 cột (Thứ Hai $\rightarrow$ Chủ Nhật). Mỗi ngày gồm các thẻ bữa ăn:
    - *Bữa sáng:* Tên món, hình ảnh thu nhỏ, thời gian nấu (15 phút), lượng calo (`350 kcal`).
    - *Bữa trưa:* Tên món, lượng đạm/carb/béo (`22g P - 60g C - 12g F`), calo (`620 kcal`).
    - *Bữa tối:* Tên món thanh đạm, dễ tiêu hóa (`480 kcal`).
  - **Nút thao tác trên từng món ăn:**
    - `[Đổi món khác (Swap)]`: Gọi AI sinh ngay món chay thay thế có cùng mức calo và loại trừ dị ứng.
    - `[Xem công thức chi tiết]`: Mở Drawer công thức từng bước.
- **Thanh tổng hợp & Xuất dữ liệu (Actions Footer):**
  - Thước đo dinh dưỡng ngày: Biểu đồ thanh ngang so sánh calo nạp so với mục tiêu TDEE ($\pm 5\%$).
  - Nút **"Xuất Danh Sách Đi Chợ (Shopping List)"**: Mở cửa sổ tổng hợp toàn bộ nguyên liệu cần mua cho cả tuần theo từng nhóm hàng (Rau củ, Đậu & Nấm, Gia vị, Ngũ cốc).
  - Cảnh báo y tế thường trực (Medical Disclaimer).

### Màn hình U-03: Quét Ảnh Tủ Lạnh & Ước Lượng Độ Tươi (Fridge Vision Scanner)
- **Khu vực Tải ảnh / Camera (Upload & Dropzone):**
  - Hỗ trợ kéo thả ảnh hoặc chụp trực tiếp: *"Kéo ảnh chụp rau củ trong tủ lạnh vào đây (Hỗ trợ JPG, PNG tối đa 5MB)"*.
  - Nút tải ảnh mẫu thử nghiệm nhanh: `[Ảnh mẫu: Súp lơ + Cà rốt + Nấm]`.
- **Bảng kết quả nhận diện thị giác máy tính (Detection Results):**
  - Ảnh preview kèm các khung nhận diện (Bounding Boxes mô phỏng).
  - Danh sách nguyên liệu dạng thẻ nhãn (Tags) tương tác:
    - `Súp lơ xanh`: Ngưỡng tin cậy `95%` | Badge: **TƯƠI** (Xanh lá - Dùng tốt 3-5 ngày).
    - `Cà rốt`: Ngưỡng tin cậy `91%` | Badge: **CẦN DÙNG NGAY** (Cam - Hơi héo vỏ, nấu trong 24h).
    - `Nấm đông cô`: Ngưỡng tin cậy `88%` | Badge: **TƯƠI** (Xanh lá).
  - Thao tác người dùng (Human-in-the-Loop): Cho phép bấm `[X]` xóa nhãn sai hoặc bấm `[+ Thêm nguyên liệu thủ công]`.
- **Gợi ý món ăn nấu được ngay (Instant Recipe Match):**
  - Danh sách thẻ món ăn phù hợp với nguyên liệu sẵn có:
    - *Món 1:* "Súp lơ xào nấm đông cô sốt dầu hào chay" (Khớp 90% nguyên liệu).
    - *Món 2:* "Canh củ quả chay thanh nhiệt" (Khớp 85% nguyên liệu).
  - Nút bấm: `[Bắt đầu nấu món này]`.

### Màn hình U-04: Trợ Lý Dinh Dưỡng AI Thông Minh (Conversational Nutritionist)
- **Giao diện Chatbot đàm thoại hiện đại:**
  - Khung tin nhắn cuộn mượt mà, phân định rõ ràng giữa tin nhắn người dùng và phản hồi AI.
  - Avatar CrocVeggie đội nón đầu bếp phản hồi thông minh.
  - **Bộ đếm lượt dùng thử (Dành cho Khách vãng lai):** Thẻ thông báo trên đầu chat: *"Chế độ dùng thử: Bạn còn 2 / 3 câu hỏi miễn phí"*.
  - Nút câu hỏi gợi ý nhanh (Quick Prompts):
    - *"Thay thế trứng trong làm bánh chay như thế nào?"*
    - *"Cách bổ sung đủ vitamin B12 cho người thuần chay?"*
    - *"Gợi ý bữa sáng chay giàu protein nạp năng lượng?"*
  - Ô nhập liệu câu hỏi kèm nút gửi và hiệu ứng gõ chữ trực tiếp (Streaming Simulation).
  - Footer thông báo miễn trừ y tế rõ ràng.

### Màn hình U-05: Khám Phá Quán Chay & Bản Đồ Địa Điểm (Nearby Vegan Places)
- **Bộ lọc vị trí & Bán kính:**
  - Bộ chọn cự ly: `1 km` | `3 km` | `5 km` | `10 km`.
  - Bộ lọc loại hình: Quán cơm chay bình dân, Nhà hàng buffet chay, Quán phở/bún bò chay, Cửa hàng thực phẩm chay sạch.
- **Bản đồ trực quan & Danh sách địa điểm:**
  - Mô phỏng bản đồ số với các ghim vị trí quán chay nổi bật.
  - Thẻ thông tin quán ăn: Tên quán, khoảng cách (`850m`), giờ mở cửa, đánh giá (`4.8 sao - 128 đánh giá`), hình ảnh món ăn đặc trưng.
  - Nút chỉ đường và nút xem thực đơn quán.

---

## 3. KIẾN TRÚC GIAO DIỆN PHÂN HỆ QUẢN TRỊ (ADMIN & AI OBSERVABILITY PORTAL)

### Màn hình A-01: Bảng Điều Khiển Tổng Quan (Admin Executive Overview)
- **Top Bar Quản Trị:**
  - Logo `VEGEAI Admin Center` với badge mascot CrocVeggie phiên bản giám sát viên (Super Croc).
  - Thanh trạng thái hệ thống: `Trạng thái: BÌNH THƯỜNG (All Systems Operational)`.
  - Thông báo khẩn cấp: Badge chuông báo đỏ khi có nội dung vi phạm mức cao cần duyệt.
- **4 Thẻ Chỉ Số KPI Cốt Lõi (Metric Cards):**
  1. **Tổng Thành Viên:** `12,840` (+14.2% so với tháng trước).
  2. **Yêu Cầu Gọi AI Hôm Nay:** `48,290 requests` (Meal Planner 42%, Chatbot 35%, Vision 23%).
  3. **Độ Trễ Phản Hồi P95 (Latency):** `1.42 giây` (Trong giới hạn an toàn $< 2.5\text{s}$).
  4. **Hàng Đợi Chờ Duyệt (Pending Moderation):** `6 bài viết` (Cần xử lý gấp).

### Màn hình A-02: Hàng Đợi Kiểm Duyệt Nội Dung AI (AI Moderation Review Queue)
- **Mục đích:** Hiện thực hóa triết lý *Human-in-the-Loop* — Quản trị viên đưa ra quyết định cuối cùng đối với các nội dung bị mô hình tự động gắn cờ vi phạm.
- **Bảng dữ liệu hàng đợi (Queue Table):**
  - **Cột 1: Đối tượng:** Loại (`Blog Post` / `Comment` / `Video Recipe`), ID bài viết, Tác giả.
  - **Cột 2: Nội dung trích dẫn:** Đoạn trích văn bản bị gắn cờ, bôi đỏ các cụm từ nghi vấn.
  - **Cột 3: Điểm vi phạm AI (AI Score):**
    - Điểm độc hại (Toxicity Score): ví dụ `0.94` (Mức độ nghiêm trọng).
    - Cờ vi phạm: `Phi Chay (Meat promotion)`, `Ngôn từ thù ghét (Toxic)`, `Spam thương mại`.
  - **Cột 4: Mô hình thẩm định:** `Gemini-Flash-Guard v2` hoặc `OpenAI Moderation API`.
  - **Cột 5: Hành động can thiệp thủ công (Admin Actions):**
    - Nút xanh **"Duyệt & Khôi phục (False Positive)"**: Bỏ cờ, xuất bản lại bài viết và ghi nhận AI nhận diện nhầm vào bộ dữ liệu huấn luyện.
    - Nút đỏ **"Gỡ bài & Phạt người dùng"**: Xóa vĩnh viễn nội dung khỏi DB và gửi thông báo kỷ luật.
    - Nút xám **"Tạm khóa 24h"**: Giữ nguyên trạng thái ẩn chờ phản hồi từ người đăng.

### Màn hình A-03: Trung Tâm Giám Sát Mô Hình AI (AI Observability & Metrics)
- **Phân bổ tải theo nhà cung cấp AI (Provider Load Breakdown):**
  - OpenAI GPT-4o-mini: 62% lưu lượng.
  - Google Gemini 1.5 Flash: 28% lưu lượng.
  - Whisper STT (Speech-to-Text): 10% lưu lượng.
- **Giám sát độ tin cậy & Circuit Breaker:**
  - Trạng thái Circuit Breaker: `CLOSED (Healthy)`.
  - Tỷ lệ lỗi (Error Rate): `0.04%`.
  - Tỷ lệ người dùng sửa kết quả AI (User Correction Rate): `8.2%` (Người dùng đồng thuận 91.8% với gợi ý của AI).
- **Nhật ký kiểm toán AI (Live Audit Logs):** Bảng ghi lại thời gian thực từng sự kiện: Timestamp, Tác tử, Hành động, Provider, Latency, Trạng thái (`SUCCESS` / `RETRY` / `FALLBACK`).

---

## 4. QUY CHUẨN TRẢI NGHIỆM NGƯỜI DÙNG (UX STATES & ERROR HANDLING)

Theo nguyên tắc trong `vegeai-ui-ux` skill, mọi màn hình trong hệ thống đều phải xử lý trọn vẹn 4 trạng thái:
1. **Trạng thái Đang tải (Loading State):** Hiển thị khung xương (Skeleton Loader) hoặc vòng xoay kèm hình ảnh chú cá sấu CrocVeggie hoạt náo (ví dụ: cá sấu đang cắt cà rốt khi chờ AI lập thực đơn).
2. **Trạng thái Rỗng (Empty State):** Khi chưa có dữ liệu (chưa quét ảnh, chưa tạo thực đơn, hoặc hàng đợi duyệt rỗng), hiển thị thông điệp tích cực kèm nút tạo mới hành động.
3. **Trạng thái Lỗi (Error State):** Báo lỗi rõ nguyên nhân kèm giải pháp khắc phục cụ thể; không hiển thị mã lỗi kỹ thuật khó hiểu (như NullPointerException hay 500 Internal Server Error) cho người dùng cuối.
4. **Trạng thái Thành công (Success State):** Hiển thị Toast thông báo xanh lá dịu mắt, tự động biến mất sau 3 giây.
