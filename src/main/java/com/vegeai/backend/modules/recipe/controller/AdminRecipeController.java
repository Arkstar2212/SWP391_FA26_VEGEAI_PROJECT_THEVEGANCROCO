package com.vegeai.backend.modules.recipe.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.recipe.dto.*;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import com.vegeai.backend.modules.recipe.service.RecipeService;
import com.vegeai.backend.modules.recipe.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "08. Admin Recipe Management", description = "Quản lý kho công thức món ăn chuẩn (Verified Recipes) và bộ từ điển thẻ tag phân loại của Quản trị viên.")
@SecurityRequirement(name = "bearerAuth")
public class AdminRecipeController {

    private final RecipeService recipeService;
    private final TagService tagService;

    @GetMapping("/recipes")
    @Operation(summary = "Lấy danh sách công thức để quản lý", description = "Hỗ trợ tìm kiếm và lọc theo trạng thái, bao gồm cả bản nháp và công thức lưu trữ.")
    public ResponseEntity<ApiResponse<com.vegeai.backend.common.dto.PageResponse<AdminRecipeSummaryDto>>> listRecipes(
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo trạng thái PUBLISHED, DRAFT hoặc ARCHIVED")
            @RequestParam(required = false) RecipeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.listAdminRecipes(keyword, status, page, size)));
    }

    @GetMapping("/recipes/{id}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.getAdminRecipe(id)));
    }

    @PostMapping("/recipes")
    @Operation(summary = "Tạo công thức món ăn mới (Admin)", description = "Thêm một công thức món ăn được kiểm định vào hệ thống, bao gồm đầy đủ định lượng calo, protein, nguyên liệu và các thẻ tag phân loại.")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> createRecipe(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateRecipeRequest request) {
        UUID authorId = (userId != null && !userId.isBlank()) ? UUID.fromString(userId) : null;
        RecipeDetailResponse response = recipeService.createRecipe(authorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/recipes/{id}")
    @Operation(summary = "Cập nhật công thức món ăn (Admin)", description = "Chỉnh sửa nội dung, định lượng dinh dưỡng, nguyên liệu hoặc các bước nấu.")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> updateRecipe(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecipeRequest request) {
        RecipeDetailResponse response = recipeService.updateRecipe(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/recipes/{id}")
    @Operation(summary = "Xóa công thức món ăn (Admin)", description = "Xóa bỏ công thức món ăn khỏi kho dữ liệu.")
    public ResponseEntity<ApiResponse<Void>> deleteRecipe(@PathVariable UUID id) {
        recipeService.deleteRecipe(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/tags")
    @Operation(summary = "Thêm thẻ tag mới vào từ điển (Admin)", description = "Thêm thẻ thuộc một trong các nhóm: FORMAT, GENRE, THEME, OCCASION, CONTENT.")
    public ResponseEntity<ApiResponse<TagDto>> createTag(
            @Valid @RequestBody CreateTagRequest request) {
        TagDto response = tagService.createTag(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/tags")
    @Operation(summary = "Lấy danh sách tất cả thẻ tag (Admin)", description = "Xem toàn bộ thẻ tag trong hệ thống để quản lý.")
    public ResponseEntity<ApiResponse<List<TagDto>>> listTags() {
        List<TagDto> tags = tagService.getAllTags();
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    @DeleteMapping("/tags/{id}")
    @Operation(summary = "Xóa thẻ tag (Admin)", description = "Xóa thẻ khỏi từ điển phân loại.")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable UUID id) {
        tagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
