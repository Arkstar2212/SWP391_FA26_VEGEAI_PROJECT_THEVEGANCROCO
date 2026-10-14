package com.vegeai.backend.modules.recipe.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.recipe.dto.*;
import com.vegeai.backend.modules.recipe.service.RecipeService;
import com.vegeai.backend.modules.recipe.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@Tag(name = "07. Recipes & Smart Filter", description = "Khám phá công thức món chay, bộ lọc đa tiêu chí phân tầng (AND/OR, loại trừ dị ứng) và so khớp nguyên liệu tủ lạnh.")
public class RecipeController {

    private final RecipeService recipeService;
    private final TagService tagService;

    @PostMapping("/filter")
    @Operation(
            summary = "Bộ lọc công thức món chay đa tiêu chí (Faceted Recipe Filter)",
            description = """
                    **Tìm kiếm và lọc món chay chuyên sâu theo cơ chế lọc đa tầng (Faceted Filter):**
                    - Hỗ trợ chọn linh hoạt nhiều nhóm thẻ: Trường phái (Format), Cách nấu (Genre), Mục tiêu thể chất (Theme), Hoàn cảnh (Occasion).
                    - **Chế độ Inclusion Mode:**
                      - `AND`: Phải thỏa mãn **TẤT CẢ** các thẻ đã chọn.
                      - `OR`: Thỏa mãn **BẤT KỲ** thẻ nào được chọn.
                    - **Chế độ Exclusion Mode (`excludeTagCodes`):**
                      - Tuyệt đối loại trừ các món có chứa thành phần kiêng cử / dị ứng của người dùng (VD: Không đậu nành `SOY_FREE`, không Gluten `GLUTEN_FREE`, không cay `NOT_SPICY`).
                    - Kết hợp lọc theo lượng calo tối đa, đạm tối thiểu, thời gian nấu, độ khó và từ khóa.
                    """
    )
    public ResponseEntity<ApiResponse<PageResponse<RecipeSummaryDto>>> filterRecipes(
            @RequestBody RecipeFilterRequest filter) {
        PageResponse<RecipeSummaryDto> response = recipeService.filterRecipes(filter);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(
            summary = "Lấy danh sách công thức phân trang (Tìm kiếm nhanh)",
            description = "Hỗ trợ tìm kiếm nhanh theo từ khóa bằng phương thức GET."
    )
    public ResponseEntity<ApiResponse<PageResponse<RecipeSummaryDto>>> listRecipes(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        RecipeFilterRequest filter = RecipeFilterRequest.builder()
                .keyword(keyword)
                .page(page)
                .size(size)
                .build();
        PageResponse<RecipeSummaryDto> response = recipeService.filterRecipes(filter);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/tags")
    @Operation(
            summary = "Lấy cây danh mục thẻ nhãn bộ lọc (Tag Explorer Tree)",
            description = """
                    **Lấy toàn bộ cây danh mục thẻ gom nhóm theo 5 nhóm chính:**
                    - `format`: Trường phái ăn chay (Thuần chay, Chay trứng sữa, Không ngũ vị tân...)
                    - `genre`: Kiểu món / Cách chế biến (Món kho, xào, canh, soup, lẩu, salad...)
                    - `theme`: Mục tiêu sức khỏe (Giàu đạm, Giảm cân, Tốt tim mạch, Bổ máu...)
                    - `occasion`: Tiện lợi & Hoàn cảnh (Nhanh < 15 phút, Nấu 1 nồi One-Pot, Tiết kiệm...)
                    - `content`: Dị ứng / Thành phần kiêng (Không đậu nành, Không gluten, Không nấm...)
                    """
    )
    public ResponseEntity<ApiResponse<TagGroupResponse>> getFilterTags() {
        TagGroupResponse tags = tagService.getAllTagsGrouped();
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    @GetMapping("/fridge-match")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "So khớp kho tủ lạnh với công thức Admin (Recipe Match Engine)",
            description = """
                    **Tính toán xem tủ lạnh của bạn hiện có thể nấu được những món nào của Admin:**
                    - Thuật toán tự động đối soát nguyên liệu đang có trong tủ (`status = ACTIVE`) với danh sách nguyên liệu của từng món.
                    - Tính toán tỷ lệ khớp (`matchPercentage` từ 0 - 100%).
                    - Phân nhóm:
                      - 🟢 `CAN_COOK_NOW` (Khớp 100%): Có thể nấu ngay!
                      - 🟡 `MISSING_FEW` (Khớp $\\ge 60\\%$): Chỉ cần mua thêm 1-2 nguyên liệu (có danh sách cụ thể).
                    """
    )
    public ResponseEntity<ApiResponse<FridgeRecipeMatchResponse>> matchRecipesWithFridge(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Tỷ lệ khớp tối thiểu (mặc định: 50%)")
            @RequestParam(defaultValue = "50") int minMatchPercentage,
            @Parameter(description = "Số lượng món tối đa cần lấy (mặc định: 20)")
            @RequestParam(defaultValue = "20") int limit) {
        FridgeRecipeMatchResponse response = recipeService.matchRecipesWithFridge(userId, minMatchPercentage, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Xem chi tiết công thức bằng ID",
            description = "Trả về thông tin chi tiết đầy đủ gồm: Dinh dưỡng (Calo, Protein, Carbs, Fat, Fiber), Định lượng nguyên liệu và Các bước nấu ăn."
    )
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipeById(
            @PathVariable UUID id) {
        RecipeDetailResponse response = recipeService.getRecipeById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/slug/{slug}")
    @Operation(
            summary = "Xem chi tiết công thức bằng SEO Slug",
            description = "Trả về chi tiết công thức thông qua đường dẫn thân thiện SEO (VD: dau-phu-sot-ca-chua-tuoi)."
    )
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipeBySlug(
            @PathVariable String slug) {
        RecipeDetailResponse response = recipeService.getRecipeBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
