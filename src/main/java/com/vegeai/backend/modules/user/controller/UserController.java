package com.vegeai.backend.modules.user.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.user.dto.ChangePasswordHashedRequest;
import com.vegeai.backend.modules.user.dto.ChangePasswordRequest;
import com.vegeai.backend.modules.user.dto.ChangeEmailRequest;
import com.vegeai.backend.modules.user.dto.PublicUserProfileResponse;
import com.vegeai.backend.modules.user.dto.QuickProfileRequest;
import com.vegeai.backend.modules.user.dto.UpdateUserProfileRequest;
import com.vegeai.backend.modules.user.dto.UserProfileResponse;
import com.vegeai.backend.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * User profile & account management endpoints.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "02. Users & Profiles", description = "Quản lý hồ sơ cá nhân, chỉ số sức khỏe, đổi mật khẩu và vòng đời tài khoản.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user's profile", description = """
            **Xem thông tin hồ sơ của tài khoản đang đăng nhập.**
            
            - **Yêu cầu:** Bấm nút **Authorize** ở góc trên Swagger và nhập `Bearer <accessToken>`.
            - Trả về thông tin cá nhân, chỉ số BMI y khoa tất định, phân loại thể trạng, chế độ ăn và dị ứng.
            """)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal String userId) {
        UserProfileResponse profile = userService.getMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user's profile", description = """
            **Cập nhật hồ sơ cá nhân và chỉ số sức khỏe.**
            
            **Quy tắc điền thông tin:**
            - `fullName`: Họ và tên (không chứa số, tối đa 100 ký tự).
            - `phoneNumber`: Đúng 10 chữ số bắt đầu bằng `0` (hoặc để trống `""`).
            - `heightCm`: Chiều cao tính bằng cm (50.0 - 280.0 cm, ví dụ: `172.5`).
            - `weightKg`: Cân nặng tính bằng kg (20.0 - 350.0 kg, ví dụ: `65.0`).
            - `dietaryPreferences`: `VEGAN`, `VEGETARIAN`, `LACTO_OVO`, `LACTO`, `OVO`, `PESCATARIAN`, `FLEXITARIAN`, `RAW_VEGAN`.
            - `allergies`: `NONE` hoặc phân tách bằng dấu phẩy (ví dụ: `PEANUTS, GLUTEN, SOY`).
            - `dailyCalorieTarget`: Mục tiêu calo hàng ngày (500 - 10,000 kcal, ví dụ: `2000`).
            
            *Lưu ý: Hệ thống sẽ tự động tính toán lại BMI và phân loại thể trạng (Gầy, Bình thường, Thừa cân, Béo phì) chuẩn y khoa khi bạn cập nhật chiều cao hoặc cân nặng.*
            """)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdateUserProfileRequest req) {
        UserProfileResponse updated = userService.updateMyProfile(userId, req);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully.", updated));
    }

    @PostMapping(value = "/me/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateAvatar(
            @AuthenticationPrincipal String userId, @RequestPart("avatar") MultipartFile avatar) {
        return ResponseEntity.ok(ApiResponse.success("Avatar updated successfully.", userService.updateAvatar(userId, avatar)));
    }

    @PutMapping("/me/email")
    @Operation(summary = "Change the current user's email")
    public ResponseEntity<ApiResponse<UserProfileResponse>> changeEmail(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ChangeEmailRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Email updated successfully.", userService.changeEmail(userId, req)));
    }

    @PutMapping("/me/quick-profile")
    @Operation(summary = "Quick setup user dietary & health profile", description = """
            **Thiết lập nhanh hồ sơ sở thích ăn chay và dị ứng (Quick Profile Modal).**
            
            - Dùng để hỗ trợ người dùng hoàn tất hồ sơ ngay tại popup modal mà không làm mất trạng thái phiên quét tủ lạnh hoặc đoạn chat AI.
            - `dietaryPreferences` là bắt buộc (ví dụ: `VEGAN`, `VEGETARIAN`, `LACTO_OVO`, v.v.).
            - `allergies`, `heightCm`, `weightKg`, `dailyCalorieTarget` là tùy chọn.
            """)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateQuickProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody QuickProfileRequest req) {
        UserProfileResponse updated = userService.updateQuickProfile(userId, req);
        return ResponseEntity.ok(ApiResponse.success("Hồ sơ ăn chay & dinh dưỡng đã được thiết lập thành công.", updated));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change password", description = """
            **Đổi mật khẩu tài khoản.**
            
            - Nhập `currentPassword` (mật khẩu hiện tại), `newPassword` và `confirmNewPassword` (mật khẩu mới).
            - `newPassword` & `confirmNewPassword`: Mật khẩu mới (tối thiểu 8 ký tự, có đủ chữ hoa, thường, số, ký tự đặc biệt).
            - *Lưu ý: Sau khi đổi mật khẩu thành công, toàn bộ phiên đăng nhập cũ trên các thiết bị khác sẽ tự động bị hủy (thu hồi Refresh Tokens).*
            """)
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(userId, req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully. Please log in with your new password on other devices.", null));
    }

    @PutMapping("/me/password-hashed")
    @Operation(summary = "Change password (SHA-256 pre-hashed)", description = """
            **Đổi mật khẩu tài khoản với mật khẩu đã băm SHA-256 phía client.**

            - Cả 3 trường `currentPassword`, `newPassword`, `confirmNewPassword` là chuỗi SHA-256 hex 64 ký tự do client băm trước (giống login/register/reset-password).
            - Độ mạnh mật khẩu mới do frontend kiểm tra trước khi băm.
            - *Lưu ý: Sau khi đổi mật khẩu thành công, toàn bộ phiên đăng nhập cũ trên các thiết bị khác sẽ tự động bị hủy (thu hồi Refresh Tokens).*
            """)
    public ResponseEntity<ApiResponse<Void>> changePasswordHashed(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ChangePasswordHashedRequest req) {
        userService.changePassword(userId, ChangePasswordRequest.builder()
                .currentPassword(req.getCurrentPassword())
                .newPassword(req.getNewPassword())
                .confirmNewPassword(req.getConfirmNewPassword())
                .build());
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully. Please log in with your new password on other devices.", null));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Deactivate current account", description = """
            **Hủy kích hoạt (vô hiệu hóa) tài khoản người dùng.**
            
            - Chuyển trạng thái tài khoản thành `DISABLED`.
            - Tự động hủy toàn bộ phiên đăng nhập và token hiện tại.
            """)
    public ResponseEntity<ApiResponse<Void>> deactivateMyAccount(
            @AuthenticationPrincipal String userId) {
        userService.deactivateMyAccount(userId);
        return ResponseEntity.ok(ApiResponse.success("Your account has been deactivated successfully.", null));
    }

    @GetMapping("/{userId}/public")
    @Operation(summary = "Get public user profile", description = """
            **Xem hồ sơ công khai của người dùng theo UUID.**
            
            - Không yêu cầu đăng nhập (Public).
            - Chỉ hiển thị thông tin cộng đồng (Username, Full Name, Avatar URL, Dietary Preferences, Ngày tham gia).
            - Các dữ liệu nhạy cảm (Email, SĐT, Chiều cao, Cân nặng, BMI) được ẩn hoàn toàn để bảo vệ quyền riêng tư (PII Protection).
            """)
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> getPublicProfile(
            @PathVariable UUID userId) {
        PublicUserProfileResponse publicProfile = userService.getPublicProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(publicProfile));
    }
}
