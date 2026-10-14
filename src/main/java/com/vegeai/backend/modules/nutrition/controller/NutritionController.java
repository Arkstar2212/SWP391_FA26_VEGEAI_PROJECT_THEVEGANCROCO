package com.vegeai.backend.modules.nutrition.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.nutrition.dto.NutritionCalculateRequest;
import com.vegeai.backend.modules.nutrition.dto.NutritionOptionsResponse;
import com.vegeai.backend.modules.nutrition.dto.NutritionProfileRequest;
import com.vegeai.backend.modules.nutrition.dto.NutritionProfileResponse;
import com.vegeai.backend.modules.nutrition.dto.NutritionTargetsResponse;
import com.vegeai.backend.modules.nutrition.service.NutritionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Deterministic nutrition endpoints: BMI, BMR, TDEE, calorie and macro targets.
 */
@RestController
@RequestMapping("/api/nutrition")
@RequiredArgsConstructor
@Tag(name = "04. Nutrition & Health", description = "Hồ sơ dinh dưỡng, BMI/BMR/TDEE và mục tiêu calo, macro (tính toán tất định, không dùng AI).")
public class NutritionController {

    private final NutritionService nutritionService;

    @GetMapping("/profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get my nutrition profile", description = """
            **Xem hồ sơ dinh dưỡng của tài khoản đang đăng nhập.**

            - Trả về thông tin đầu vào và các chỉ số được tính lại theo tuổi hiện tại: BMI, BMR, TDEE, calo mục tiêu, protein/carbs/fat/fiber.
            - Trả `404` (code `2001`) nếu người dùng chưa thiết lập hồ sơ — frontend nên chuyển sang màn hình onboarding.
            """)
    public ResponseEntity<ApiResponse<NutritionProfileResponse>> getMyProfile(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.getMyProfile(userId)));
    }

    @PutMapping("/profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create or update my nutrition profile", description = """
            **Tạo mới hoặc cập nhật hồ sơ dinh dưỡng.**

            - `gender`: `MALE` | `FEMALE`
            - `activityLevel`: `SEDENTARY` (×1.2), `LIGHT` (×1.375), `MODERATE` (×1.55), `ACTIVE` (×1.725), `VERY_ACTIVE` (×1.9)
            - `goal`: `LOSE_WEIGHT` (−500 kcal), `MAINTAIN`, `GAIN_MUSCLE` (+300 kcal)
            - `customDailyCalories` (tuỳ chọn): tự đặt calo/ngày; để `null` để hệ thống tự tính.

            *Công thức:* BMR theo Mifflin-St Jeor → TDEE = BMR × hệ số vận động → cộng/trừ theo mục tiêu,
            không thấp hơn 1200 kcal (nữ) / 1500 kcal (nam). Đồng thời cập nhật chiều cao, cân nặng, BMI và calo mục tiêu trong `/api/users/me`.
            """)
    public ResponseEntity<ApiResponse<NutritionProfileResponse>> upsertMyProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody NutritionProfileRequest req) {
        NutritionProfileResponse res = nutritionService.upsertMyProfile(userId, req);
        return ResponseEntity.ok(ApiResponse.success("Nutrition profile saved successfully.", res));
    }

    @DeleteMapping("/profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete my nutrition profile", description = "**Xoá hồ sơ dinh dưỡng** (để thiết lập lại từ đầu).")
    public ResponseEntity<ApiResponse<Void>> deleteMyProfile(@AuthenticationPrincipal String userId) {
        nutritionService.deleteMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Nutrition profile deleted successfully.", null));
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate nutrition targets (public)", description = """
            **Máy tính dinh dưỡng công khai — không cần đăng nhập, không lưu dữ liệu.**

            Dùng cho khách (Guest) hoặc bước xem trước khi onboarding. Nhập `age` thay vì ngày sinh.
            """)
    public ResponseEntity<ApiResponse<NutritionTargetsResponse>> calculate(
            @Valid @RequestBody NutritionCalculateRequest req) {
        return ResponseEntity.ok(ApiResponse.success(nutritionService.calculatePreview(req)));
    }

    @GetMapping("/options")
    @Operation(summary = "List nutrition enum options (public)", description = "Danh sách giá trị cho dropdown: giới tính, mức vận động, mục tiêu.")
    public ResponseEntity<ApiResponse<NutritionOptionsResponse>> options() {
        return ResponseEntity.ok(ApiResponse.success(NutritionOptionsResponse.create()));
    }
}
