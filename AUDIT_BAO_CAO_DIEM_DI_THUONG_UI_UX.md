# BÁO CÁO RÀ SOÁT CÁC ĐIỂM DỊ THƯỜNG (ANOMALIES), UPLOAD MEDIA & UI/UX TOÀN HỆ THỐNG VEGEAI

**Ngày kiểm tra:** 10/11/2026  
**Phạm vi kiểm tra:** Toàn bộ Frontend (`SWP391_VEGEAI_FRONTEND`) & Backend (`SWP391_FA26_VEGEAI_PROJECT_THEVEGANCROCO`)  
**Nhánh cập nhật (Git Branch):** `fix/ui-upload-anomalies-audit`

---

## 1. Tóm tắt tổng quan (Executive Summary)

Qua quá trình rà soát toàn diện các luồng nghiệp vụ, giao diện (Light/Dark Mode), cơ chế tải lên hình ảnh/video (Media Upload), phân quyền (Guest / Member / Admin) và sự đồng bộ DTO giữa Frontend — Backend sau các đợt gộp nhánh (`git merge`), hệ thống ghi nhận **8 nhóm điểm dị thường / bất hợp lý chính**, trong đó tập trung nhiều nhất ở:
1. **Thiếu tùy chọn tải file trực tiếp (Upload file từ thiết bị)** ở trình soạn thảo Blog và trình quản trị Công thức (chỉ cho dán URL ngoài).
2. **Backend từ chối URL file vừa upload hoặc làm rơi trường dữ liệu** (`videoUrl`, `ratingAverage`, `ratingCount` trong Blog; endpoint bình luận Blog bị thiếu gây lỗi 404).
3. **Không hiển thị trình phát `<video>` cho các file video MP4/WebM tải lên trực tiếp** trong Blog (chỉ kiểm tra `iframe` YouTube).
4. **Mất khối Đánh giá & Upload ảnh thành phẩm (`ContentInteractions`)** ở trang Chi tiết Công thức.
5. **UX tải lên Video & Avatar và lỗi tương phản Dark Mode** ở một số màn hình quản trị.

Tất cả các điểm dị thường nghiêm trọng dưới đây **đã được khắc phục trực tiếp trong mã nguồn** và kiểm thử biên dịch thành công trên nhánh `fix/ui-upload-anomalies-audit`.

---

## 2. Chi tiết các điểm dị thường phát hiện & Giải pháp đã xử lý

### Điểm dị thường #1: Trình soạn thảo Blog (`BlogEditorPage.tsx`) không có tùy chọn tải ảnh bìa & video từ thiết bị
- **Hiện trạng trước khi sửa:**
  - Tại trang Viết bài / Chỉnh sửa Blog (`/blog/write`, `/blog/my-posts/:id/edit`), tab **"Ảnh bìa"** chỉ có ô nhập text URL (`https://...`) và 3 ảnh mẫu cố định; người dùng **không có nút chọn file ảnh từ máy tính** (`<input type="file">`).
  - Tab **"Đính kèm Video"** chỉ cho phép dán link YouTube (`https://www.youtube.com/watch?v=...`), **không có tùy chọn tải file video MP4/WebM từ máy tính** dù Backend đã có sẵn `CommunityMediaAssetController` (`/api/videos/upload`).
  - Nghiêm trọng hơn: Nếu đính kèm video dạng file tải lên (`/api/public/community-media/*.mp4`, `videoSourceObj.kind === 'file'`), cả trang Xem trước (`BlogEditorPage.tsx`) lẫn trang Chi tiết bài viết (`BlogPage.tsx`) chỉ kiểm tra `videoSourceObj?.kind === 'embed'` nên **không hiển thị trình phát `<video controls>`**, khiến video tải lên bị ẩn hoàn toàn.
- **Đã khắc phục:**
  - Bổ sung nút **Tải ảnh bìa từ thiết bị (JPG, PNG, WebP - tối đa 5MB)** và **Tải file Video từ thiết bị (MP4, WebM - tối đa 60MB)** kèm trạng thái loading (`uploadingMedia`) và nút **Gỡ video đính kèm** trong `src/pages/BlogEditorPage.tsx`.
  - Bổ sung nhánh render `<video controls>` khi `videoSourceObj?.kind === 'file'` tại cả `src/pages/BlogEditorPage.tsx`, `src/pages/BlogPage.tsx` và `src/pages/BlogPostStatusPage.tsx`.

---

### Điểm dị thường #2: Backend `BlogService` & `BlogDtos` từ chối ảnh upload nội bộ và làm mất `videoUrl`, `ratingAverage`, `ratingCount`
- **Hiện trạng trước khi sửa:**
  - Trong `BACKEND/.../BlogService.java` (hàm `cover()`) và `PersonalBlogActionsService.java` (hàm `validateCover()`), biểu thức chính quy chỉ cho phép `/images/[A-Za-z0-9._-]+` hoặc `http(s)://...`, dẫn đến việc khi người dùng upload ảnh qua `/api/videos/upload` (trả về đường dẫn `/api/public/community-media/<uuid>.(jpg|png|webp)`), Backend **báo lỗi `400 VALIDATION_ERROR` và từ chối lưu bài viết**.
  - Entity `BlogPost.java` có đầy đủ cột `videoUrl`, `ratingAverage`, `ratingCount`, nhưng `BlogDtos.PostInput` và `BlogDtos.PostResponse` bị thiếu các trường này sau khi merge nhánh, khiến mọi video đính kèm vào Blog bị mất trắng sau khi bấm Lưu.
- **Đã khắc phục:**
  - Cập nhật `BlogDtos.java`, `BlogService.java`, và `PersonalBlogActionsService.java` để nhận và trả về đầy đủ `videoUrl`, `ratingAverage`, `ratingCount`.
  - Cho phép đường dẫn `/api/public/community-media/[a-f0-9-]{36}\.(jpg|png|webp)` cho ảnh bìa và `/api/public/community-media/[a-f0-9-]{36}\.(mp4|webm)` cho video đính kèm.

---

### Điểm dị thường #3: Lỗi 404 khi tải hoặc gửi Bình luận & Đánh giá 5 sao trên trang Chi tiết Blog
- **Hiện trạng trước khi sửa:**
  - `SecurityConfig.java` (dòng 97) đã mở quyền truy cập cho `/api/blog/posts/*/comments`, và `blogService.ts` ở Frontend gọi `GET /api/blog/posts/{id}/comments` và `POST /api/blog/posts/{id}/comments`.
  - Tuy nhiên, `BlogController.java` và `BlogService.java` bị thiếu 2 endpoint này sau lần merge trước, dẫn đến việc gửi bình luận/đánh giá sao trên bài viết Blog trả về lỗi `404 Not Found`.
- **Đã khắc phục:**
  - Khôi phục đầy đủ `GET /api/blog/posts/{id}/comments` và `POST /api/blog/posts/{id}/comments` trong `BlogController.java` và `BlogService.java`, đồng thời tự động tính toán lại `ratingAverage` và `ratingCount` của bài viết khi có đánh giá mới.

---

### Điểm dị thường #4: Trình tạo/sửa Công thức của Admin (`AdminRecipeEditor.tsx`) chỉ có ô nhập URL ảnh, thiếu nút Upload ảnh & Xem trước
- **Hiện trạng trước khi sửa:**
  - Trong `src/components/AdminRecipeEditor.tsx`, mục ảnh món ăn chỉ là một ô `<input inputMode="url">` đơn điệu ghi `"Ảnh URL"`, không có nút tải ảnh từ máy tính và không có khung xem trước ảnh (Preview), gây bất tiện lớn khi Admin thêm món ăn mới.
  - Các ô nhập Nguyên liệu (`Tên`, `Số lượng`, `Đơn vị`) và Bước nấu không có `placeholder` hướng dẫn.
- **Đã khắc phục:**
  - Bổ sung nút **Tải ảnh từ máy (JPG, PNG, WebP)** kết nối với `communityService.uploadVideoAsset(file, false)`, giữ song song ô nhập URL, và thêm khung **Xem trước ảnh minh họa hiện tại** kèm nút Xóa ảnh nhanh.
  - Bổ sung `placeholder` rõ ràng cho từng ô nguyên liệu và từng bước chế biến.

---

### Điểm dị thường #5: Trang Chi tiết Công thức (`RecipeDetailPage.tsx`) bị thiếu khu vực Đánh giá & Tải ảnh thành phẩm (`ContentInteractions`)
- **Hiện trạng trước khi sửa:**
  - Hệ thống đã xây dựng sẵn component `src/components/ContentInteractions.tsx` hỗ trợ chấm điểm 1–5 sao, chọn thẻ nhận xét nhanh, và **tải lên tối đa 4 ảnh chụp món ăn thực tế ("Ảnh thành phẩm của bạn")**, đồng bộ với trang Quản trị Tương tác (`/admin/interactions`).
  - Tuy nhiên, `RecipeDetailPage.tsx` lại không render `<ContentInteractions type="RECIPE" id={recipe.recipeId} />`, khiến người dùng xem công thức không thể gửi ảnh thành phẩm hay đánh giá món ăn.
- **Đã khắc phục:**
  - Tích hợp `<ContentInteractions type="RECIPE" id={recipe.recipeId} />` vào cuối trang `src/pages/RecipeDetailPage.tsx`.

---

### Điểm dị thường #6: Bất hợp lý về UX & Trạng thái hiển thị khi Tải lên Video (`AdminVideoEditorPage.tsx`)
- **Hiện trạng trước khi sửa:**
  - Tại trang Đăng video của thành viên (`/videos/new`, `/videos/mine/:id/edit`) và Admin (`/admin/videos/new`):
    1. Thẻ `<input type="file">` mặc định của trình duyệt bị đặt chồng trực tiếp ngay dưới ô `<input>` URL mà không có nút bấm rõ ràng hay trạng thái đang tải từng loại tệp (Video vs Ảnh bìa).
    2. Khi tải file video MP4/WebM từ máy tính, người dùng vẫn phải tự tính và nhập tay số giây vào ô **Thời lượng (giây)**.
    3. Cột Xem trước bên phải chỉ hiển thị ảnh bìa (`VideoCover`), không có trình phát `<video>` hoặc `<iframe>` để kiểm tra video vừa tải lên có chạy được hay không.
    4. Đối với thành viên thường (`member = true`), ô chọn **Trạng thái hiển thị** ghi là `"Công khai"`, trong khi thực tế Backend (`VideoService.createMember`) chuyển mọi video không phải bản nháp của thành viên sang trạng thái `PENDING_REVIEW` (`Chờ duyệt`). Ngoài ra, khi sửa một video đang `PENDING_REVIEW`, giá trị của `<select>` bị lệch vì không khớp với `<option value="PUBLISHED">`.
- **Đã khắc phục:**
  - Thiết kế lại cụm nút **Tải file MP4 / WebM từ máy** và **Tải ảnh bìa từ máy (JPG, PNG, WebP)** trực quan kèm spinner riêng cho từng tác vụ.
  - Tự động đọc metadata của file video (`HTMLVideoElement.onloadedmetadata`) để **tự động điền Thời lượng (giây)** ngay khi người dùng chọn file từ máy.
  - Bổ sung trình phát Video trực tiếp (`<video controls>` / `<iframe>`) ngay trong khung Xem trước.
  - Hiển thị đúng nhãn trạng thái cho thành viên: `"Gửi duyệt công khai (Chờ kiểm duyệt)"` và đồng bộ trạng thái `PENDING_REVIEW` khi mở form chỉnh sửa.

---

### Điểm dị thường #7: Thiếu thao tác Quản lý bài viết cá nhân đã xuất bản / đã ẩn (`BlogPostStatusPage.tsx` & `MyBlogPostsPage.tsx`)
- **Hiện trạng trước khi sửa:**
  - Backend đã có sẵn `PersonalBlogActionsController` (`PUT /api/blog/my-posts/{id}/request-review`, `DELETE /api/blog/my-posts/{id}/permanent`) và `BlogController` (`/archive`, `/restore`).
  - Nhưng ở Frontend (`BlogPostStatusPage.tsx`, `MyBlogPostsPage.tsx`, `BlogEditorPage.tsx`), thành viên sở hữu bài viết ở trạng thái `PUBLISHED` không thấy nút **Chỉnh sửa bài viết**, **Ẩn bài viết**, hoặc **Xóa vĩnh viễn**; và khi bài viết ở trạng thái `ARCHIVED` (`Đã ẩn`), người dùng không có nút **Khôi phục bài viết**.
  - Trang duyệt bài của Admin (`BlogPostStatusPage.tsx`) cũng chỉ hiển thị ảnh bìa mà không hiển thị video đính kèm (`post.videoUrl`), khiến Admin duyệt bài có video không xem được nội dung video.
- **Đã khắc phục:**
  - Bổ sung các hàm `archive`, `restore`, `requestPublishedEdit`, `deletePermanent` vào `src/services/blogService.ts`.
  - Mở đầy đủ các nút thao tác **Chỉnh sửa bài viết** (gửi bản sửa đổi chờ duyệt đối với Member), **Ẩn bài viết**, **Khôi phục bài viết**, và **Xóa vĩnh viễn** trên `BlogPostStatusPage.tsx` và `MyBlogPostsPage.tsx`.
  - Hiển thị trình phát video đính kèm ngay trên trang trạng thái / kiểm duyệt bài viết.

---

### Điểm dị thường #8: Giao diện nút Đổi Avatar bị tràn/che khuất & Lỗi tương phản Dark Mode ở trang Quản trị
- **Hiện trạng trước khi sửa:**
  - Trong `src/pages/AccountSettingsPage.tsx` (dòng 505), nút `"Đổi avatar"` dùng `absolute -bottom-7` với cỡ chữ `11px` nằm lơ lửng bên ngoài vòng tròn avatar, dễ bị che khuất hoặc đè lên bố cục trên màn hình nhỏ, và chưa hỗ trợ định dạng `image/webp`.
  - Trong `src/components/AdminRecipeWorkspace.tsx` (`TagManager`) và `src/components/RecipeVideoPicker.tsx`, các ô `<input>` và `<select>` thiếu class `dark:bg-slate-950 dark:text-white dark:border-slate-700`, làm chữ bị chìm hoặc nền trắng chói khi bật chế độ Dark Mode.
  - Lỗi cấu hình Google Sign-In (`google.accounts.id.initialize()` gọi lặp và lỗi `origin_mismatch` trên popup iframe) và lỗi CORS `403 Forbidden` khi trình duyệt chạy trên cổng phát triển phụ (`5174`) hoặc IP mạng LAN.
- **Đã khắc phục:**
  - Chuyển nút đổi ảnh đại diện trong `AccountSettingsPage.tsx` thành nút pill `📷 Đổi ảnh đại diện` cân đối bên dưới khung avatar, hỗ trợ cả `PNG, JPEG, WebP`.
  - Bổ sung đầy đủ biến thể Dark Mode cho `AdminRecipeWorkspace.tsx`, `AdminRecipeEditor.tsx`, và `RecipeVideoPicker.tsx`.
  - Chuẩn hóa Google OAuth2 sang `initTokenClient` trong `LoginForm.tsx` và cập nhật `allowedOriginPatterns` trong `SecurityConfig.java`.

---

## 3. Danh sách các file đã tạo / chỉnh sửa

### Backend (`SWP391_FA26_VEGEAI_PROJECT_THEVEGANCROCO`)
1. `src/main/java/com/vegeai/backend/common/config/SecurityConfig.java` — Mở rộng CORS `allowedOriginPatterns` cho các cổng localhost và mạng nội bộ LAN.
2. `src/main/java/com/vegeai/backend/modules/community/dto/BlogDtos.java` — Bổ sung `videoUrl`, `ratingAverage`, `ratingCount`, `BlogCommentInput`, `BlogCommentResponse`.
3. `src/main/java/com/vegeai/backend/modules/community/controller/BlogController.java` — Bổ sung `GET /api/blog/posts/{id}/comments` và `POST /api/blog/posts/{id}/comments`.
4. `src/main/java/com/vegeai/backend/modules/community/service/BlogService.java` — Xử lý lưu/trả về `videoUrl`, hỗ trợ URL ảnh/video tải lên nội bộ (`/api/public/community-media/...`), và xử lý bình luận + tính điểm đánh giá trung bình.
5. `src/main/java/com/vegeai/backend/modules/community/service/PersonalBlogActionsService.java` — Đồng bộ kiểm tra hợp lệ cho `coverImage` và `videoUrl` từ kho media nội bộ khi thành viên gửi bản chỉnh sửa bài viết đã đăng.

### Frontend (`SWP391_VEGEAI_FRONTEND`)
1. `src/pages/BlogEditorPage.tsx` — Thêm tùy chọn tải file ảnh bìa & tải file video MP4/WebM từ thiết bị, xem trước `<video controls>`, và hỗ trợ gửi duyệt bản chỉnh sửa bài viết đã xuất bản.
2. `src/pages/BlogPage.tsx` — Hỗ trợ phát video tải lên từ máy (`<video controls>`) bên cạnh video nhúng YouTube.
3. `src/pages/BlogPostStatusPage.tsx` — Hiển thị video đính kèm khi xem/duyệt bài, bổ sung thao tác Ẩn bài viết, Khôi phục bài viết, Chỉnh sửa và Xóa vĩnh viễn.
4. `src/pages/MyBlogPostsPage.tsx` — Cho phép truy cập chỉnh sửa đối với cả bài viết đang `PUBLISHED`.
5. `src/services/blogService.ts` — Bổ sung `archive`, `restore`, `requestPublishedEdit`, `deletePermanent`.
6. `src/pages/AdminVideoEditorPage.tsx` — Thiết kế lại nút tải file Video & Ảnh bìa, tự động nhận diện thời lượng video (giây) từ file MP4/WebM, thêm khung phát thử video, và làm rõ nhãn trạng thái chờ duyệt cho thành viên.
7. `src/components/AdminRecipeEditor.tsx` — Thêm nút tải ảnh món ăn trực tiếp từ thiết bị kèm ảnh xem trước (Preview) và bổ sung placeholder cho nguyên liệu/bước nấu.
8. `src/components/AdminRecipeWorkspace.tsx` & `src/components/RecipeVideoPicker.tsx` — Sửa lỗi thiếu class hiển thị ở chế độ Dark Mode.
9. `src/pages/RecipeDetailPage.tsx` — Khôi phục khối `<ContentInteractions type="RECIPE" />` (đánh giá sao, nhận xét nhanh và tải ảnh thành phẩm món ăn).
10. `src/pages/AccountSettingsPage.tsx` — Cải thiện bố cục nút `📷 Đổi ảnh đại diện` và hỗ trợ thêm định dạng `WebP`.
11. `src/components/LoginForm.tsx` — Sửa lỗi Google OAuth2 (`initTokenClient`) tránh xung đột `google.accounts.id.initialize()`.
