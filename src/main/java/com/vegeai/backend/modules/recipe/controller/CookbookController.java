package com.vegeai.backend.modules.recipe.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.recipe.dto.*;
import com.vegeai.backend.modules.recipe.service.CookbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recipes/cookbook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "11. Personal Cookbook & Saved Recipes", description = "Sổ tay món ăn cá nhân: Lưu và quản lý lịch sử các công thức món ăn AI chế biến, tùy biến nguyên liệu và ghi chú nấu nướng.")
@SecurityRequirement(name = "bearerAuth")
public class CookbookController {

    private final CookbookService cookbookService;

    @PostMapping
    @Operation(
            summary = "Lưu công thức vào Sổ tay món ăn của tôi (Personal Cookbook)",
            description = """
                    **Lưu công thức món ăn cá nhân:**
                    - Lưu lại công thức do AI sinh ra (sau khi người dùng bấm 'Chế biến' từ quét tủ lạnh hoặc từ gợi ý AI Chef).
                    - Hỗ trợ lưu trữ đầy đủ định lượng nguyên liệu, các bước nấu, calo, độ khó và ghi chú dị ứng.
                    - Cho phép đính kèm ghi chú kinh nghiệm nấu hoặc khẩu vị riêng của người dùng.
                    """
    )
    public ResponseEntity<ApiResponse<CookbookRecipeResponse>> saveCookbookRecipe(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SaveCookbookRecipeRequest request) {
        log.info("[COOKBOOK-API] User '{}' saving cookbook recipe: '{}'", userId, request.getTitle());
        CookbookRecipeResponse response = cookbookService.saveCookbookRecipe(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lưu công thức vào sổ tay thành công.", response));
    }

    @GetMapping
    @Operation(
            summary = "Xem danh sách công thức trong Sổ tay món ăn của tôi (Phân trang & Tìm kiếm)",
            description = """
                    **Lấy danh sách các món ăn đã lưu hoặc do AI chế biến của người dùng:**
                    - Sắp xếp theo thứ tự thời gian tạo mới nhất (`createdAt DESC`).
                    - Hỗ trợ tìm kiếm theo tên món hoặc mô tả thông qua tham số `keyword`.
                    - Hỗ trợ phân trang: `page` (bắt đầu từ 0), `size` (mặc định 12).
                    """
    )
    public ResponseEntity<ApiResponse<PageResponse<CookbookRecipeSummaryDto>>> getCookbookRecipes(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Từ khóa tìm kiếm theo tên món hoặc mô tả")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng mục mỗi trang")
            @RequestParam(defaultValue = "12") int size) {
        PageResponse<CookbookRecipeSummaryDto> response = cookbookService.getCookbookRecipes(userId, keyword, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Xem chi tiết một công thức trong Sổ tay cá nhân",
            description = "Trả về thông tin chi tiết đầy đủ gồm: danh sách nguyên liệu, định lượng, từng bước nấu ăn, lưu ý dị ứng và ghi chú cá nhân."
    )
    public ResponseEntity<ApiResponse<CookbookRecipeResponse>> getCookbookRecipeDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        CookbookRecipeResponse response = cookbookService.getCookbookRecipeDetail(userId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Cập nhật công thức hoặc ghi chú cá nhân trong Sổ tay",
            description = "Cho phép người dùng chỉnh sửa định lượng, thời gian, thêm ghi chú khẩu vị (VD: 'Lần sau nấu bớt muối', 'Thay nấm bào ngư bằng nấm đùi gà')."
    )
    public ResponseEntity<ApiResponse<CookbookRecipeResponse>> updateCookbookRecipe(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCookbookRecipeRequest request) {
        CookbookRecipeResponse response = cookbookService.updateCookbookRecipe(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật công thức trong sổ tay thành công.", response));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Xóa công thức khỏi Sổ tay món ăn của tôi",
            description = "Xóa bỏ một món ăn không còn cần lưu trữ khỏi sổ tay cá nhân."
    )
    public ResponseEntity<ApiResponse<Void>> deleteCookbookRecipe(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        cookbookService.deleteCookbookRecipe(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Xóa công thức khỏi sổ tay thành công.", null));
    }
}
