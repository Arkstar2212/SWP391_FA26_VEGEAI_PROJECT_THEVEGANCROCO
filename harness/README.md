# VEGEAI TEST HARNESS & AUTOMATION GUIDE

Thư mục này chứa bộ khung kiểm thử tự động (**Harness Engineering**) cho dự án **VEGEAI** nhằm kiểm soát rủi ro, bảo vệ các ràng buộc cứng (dị ứng, calo), và đảm bảo chất lượng hệ thống mà không tốn chi phí gọi API mô hình thật.

---

## 1. Cấu Trúc Khung Kiểm Thử (Harness Architecture)

```text
harness/
├── playwright.config.ts        # Cấu hình Playwright Headless đa trình duyệt & timeout
├── package.json                # Dependencies cho test runner
├── README.md                   # Tài liệu hướng dẫn sử dụng
└── e2e/                        # Kịch bản kiểm thử E2E
    ├── guest_ai_chat.spec.ts   # Test giới hạn 3 câu hỏi của khách & modal đăng ký
    └── meal_plan_allergy.spec.ts # Test ràng buộc cứng không lọt chất gây dị ứng
```

---

## 2. Hướng Dẫn Cài Đặt & Chạy MS Playwright (Headless)

### Bước 1: Cài đặt dependencies
```bash
cd harness
npm install -D @playwright/test
npx playwright install --with-deps
```

### Bước 2: Chạy kiểm thử ở chế độ Headless
```bash
# Chạy toàn bộ test suites ở chế độ không đầu (Headless)
npx playwright test

# Chạy riêng trên trình duyệt Chromium Headless
npx playwright test --project=chromium-headless

# Xem báo cáo HTML chi tiết sau khi chạy
npx playwright show-report
```

---

## 3. Cơ Chế Mocking AI Trong Test Harness
Các API AI bên ngoài (OpenAI, Gemini) được Playwright chặn bắt (`page.route()`) để:
1. **Không tiêu tốn Token:** Tiết kiệm 100% chi phí gọi API trong quá trình CI/CD.
2. **Loại bỏ Flakiness:** Không phụ thuộc vào tốc độ mạng hay thời gian phản hồi biến thiên của mô hình.
3. **Kiểm thử các tình huống nguy hiểm:** Bơm giả lập các trường hợp lỗi (Timeout 15s, Ảo giác nguyên liệu, Lỗi JSON cắt ngắn) để kiểm tra độ vững chắc (Resilience) của Frontend & Backend.

---

## 4. Tích Hợp Selenium Headless (CI Pipeline)
Trong môi trường CI/CD (GitHub Actions / Jenkins), Selenium WebDriver được cấu hình chạy headless song song:
```python
# Ví dụ cấu hình Selenium Chrome Headless trong Python/Java
from selenium import webdriver
from selenium.webdriver.chrome.options import Options

options = Options()
options.add_argument("--headless=new")
options.add_argument("--disable-gpu")
options.add_argument("--window-size=1920,1080")
driver = webdriver.Chrome(options=options)
```
Tập trung kiểm tra độ tương thích giao diện Responsive (Mobile 375px, Tablet 768px, Desktop 1920px).
