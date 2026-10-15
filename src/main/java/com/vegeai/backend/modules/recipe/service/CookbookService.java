package com.vegeai.backend.modules.recipe.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.ai.dto.GeneratedRecipeAiResponse;
import com.vegeai.backend.modules.recipe.dto.*;
import com.vegeai.backend.modules.recipe.entity.CustomRecipe;
import com.vegeai.backend.modules.recipe.repository.CustomRecipeRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CookbookService {

    private final CustomRecipeRepository customRecipeRepository;
    private final UserRepository userRepository;

    /**
     * Lưu công thức thủ công hoặc từ giao diện vào sổ tay cá nhân.
     */
    @Transactional
    public CookbookRecipeResponse saveCookbookRecipe(String userIdStr, SaveCookbookRecipeRequest request) {
        User user = resolveUser(userIdStr);

        CustomRecipe entity = CustomRecipe.builder()
                .userId(user.getUserId())
                .title(request.getTitle())
                .description(request.getDescription())
                .cookTimeMinutes(request.getCookTimeMinutes())
                .difficulty(request.getDifficulty() != null ? request.getDifficulty() : "EASY")
                .estimatedCalories(request.getEstimatedCalories())
                .dietaryType(request.getDietaryType() != null ? request.getDietaryType() : "VEGAN")
                .originSource(request.getOriginSource() != null ? request.getOriginSource() : "MANUAL")
                .ingredients(request.getIngredients() != null ? new ArrayList<>(request.getIngredients()) : new ArrayList<>())
                .instructions(request.getInstructions() != null ? new ArrayList<>(request.getInstructions()) : new ArrayList<>())
                .safetyNotes(request.getSafetyNotes() != null ? new ArrayList<>(request.getSafetyNotes()) : new ArrayList<>())
                .sourceNotes(request.getSourceNotes())
                .originRecipeId(request.getOriginRecipeId())
                .imageUrl(request.getImageUrl())
                .build();

        CustomRecipe saved = customRecipeRepository.save(entity);
        log.info("[COOKBOOK] User '{}' saved recipe '{}' (ID: {}) to cookbook", user.getUsername(), saved.getTitle(), saved.getId());
        return mapToResponse(saved);
    }

    /**
     * Lưu công thức do AI sinh ra (Cook Now / AI Recipe Generator) vào sổ tay người dùng.
     */
    @Transactional
    public CustomRecipe saveGeneratedRecipe(UUID userId, GeneratedRecipeAiResponse generated, String contextNotes) {
        CustomRecipe entity = CustomRecipe.builder()
                .userId(userId)
                .title(generated.getTitle() != null ? generated.getTitle() : "Món chay tự tạo")
                .description(generated.getDescription())
                .cookTimeMinutes(generated.getCookTimeMinutes())
                .difficulty(generated.getDifficulty() != null ? generated.getDifficulty() : "EASY")
                .estimatedCalories(generated.getEstimatedCalories())
                .dietaryType(generated.getDietaryType() != null ? generated.getDietaryType() : "VEGAN")
                .originSource("AI_GENERATED")
                .ingredients(generated.getIngredients() != null ? new ArrayList<>(generated.getIngredients()) : new ArrayList<>())
                .instructions(generated.getInstructions() != null ? new ArrayList<>(generated.getInstructions()) : new ArrayList<>())
                .safetyNotes(generated.getSafetyNotes() != null ? new ArrayList<>(generated.getSafetyNotes()) : new ArrayList<>())
                .sourceNotes(contextNotes)
                .build();

        CustomRecipe saved = customRecipeRepository.save(entity);
        log.info("[COOKBOOK] Automatically saved AI-generated recipe '{}' (ID: {}) for user UUID '{}'",
                saved.getTitle(), saved.getId(), userId);
        return saved;
    }

    /**
     * Lấy danh sách phân trang các món trong sổ tay cá nhân của người dùng.
     */
    @Transactional(readOnly = true)
    public PageResponse<CookbookRecipeSummaryDto> getCookbookRecipes(String userIdStr, String keyword, int page, int size) {
        User user = resolveUser(userIdStr);
        int pageNumber = Math.max(0, page);
        int pageSize = (size <= 0 || size > 100) ? 12 : size;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        Page<CustomRecipe> pageResult;
        if (keyword != null && !keyword.trim().isEmpty()) {
            pageResult = customRecipeRepository.searchByUserIdAndKeyword(user.getUserId(), keyword.trim(), pageable);
        } else {
            pageResult = customRecipeRepository.findByUserIdOrderByCreatedAtDesc(user.getUserId(), pageable);
        }

        List<CookbookRecipeSummaryDto> summaryList = pageResult.getContent().stream()
                .map(this::mapToSummaryDto)
                .toList();

        return PageResponse.from(pageResult, summaryList);
    }

    /**
     * Lấy chi tiết công thức trong sổ tay cá nhân.
     */
    @Transactional(readOnly = true)
    public CookbookRecipeResponse getCookbookRecipeDetail(String userIdStr, UUID recipeId) {
        User user = resolveUser(userIdStr);
        CustomRecipe recipe = customRecipeRepository.findByIdAndUserId(recipeId, user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.COOKBOOK_RECIPE_NOT_FOUND,
                        "Không tìm thấy công thức này trong sổ tay của bạn."));

        return mapToResponse(recipe);
    }

    /**
     * Cập nhật thông tin hoặc ghi chú kinh nghiệm trong công thức sổ tay cá nhân.
     */
    @Transactional
    public CookbookRecipeResponse updateCookbookRecipe(String userIdStr, UUID recipeId, UpdateCookbookRecipeRequest request) {
        User user = resolveUser(userIdStr);
        CustomRecipe recipe = customRecipeRepository.findByIdAndUserId(recipeId, user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.COOKBOOK_RECIPE_NOT_FOUND,
                        "Không tìm thấy công thức này trong sổ tay của bạn."));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            recipe.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            recipe.setDescription(request.getDescription());
        }
        if (request.getCookTimeMinutes() != null) {
            recipe.setCookTimeMinutes(request.getCookTimeMinutes());
        }
        if (request.getDifficulty() != null && !request.getDifficulty().isBlank()) {
            recipe.setDifficulty(request.getDifficulty());
        }
        if (request.getEstimatedCalories() != null) {
            recipe.setEstimatedCalories(request.getEstimatedCalories());
        }
        if (request.getDietaryType() != null && !request.getDietaryType().isBlank()) {
            recipe.setDietaryType(request.getDietaryType());
        }
        if (request.getIngredients() != null) {
            recipe.setIngredients(new ArrayList<>(request.getIngredients()));
        }
        if (request.getInstructions() != null) {
            recipe.setInstructions(new ArrayList<>(request.getInstructions()));
        }
        if (request.getSafetyNotes() != null) {
            recipe.setSafetyNotes(new ArrayList<>(request.getSafetyNotes()));
        }
        if (request.getSourceNotes() != null) {
            recipe.setSourceNotes(request.getSourceNotes());
        }
        if (request.getImageUrl() != null) {
            recipe.setImageUrl(request.getImageUrl());
        }

        CustomRecipe saved = customRecipeRepository.save(recipe);
        log.info("[COOKBOOK] User '{}' updated cookbook recipe ID: {}", user.getUsername(), recipeId);
        return mapToResponse(saved);
    }

    /**
     * Xóa công thức khỏi sổ tay cá nhân.
     */
    @Transactional
    public void deleteCookbookRecipe(String userIdStr, UUID recipeId) {
        User user = resolveUser(userIdStr);
        if (!customRecipeRepository.existsByIdAndUserId(recipeId, user.getUserId())) {
            throw new AppException(ErrorCode.COOKBOOK_RECIPE_NOT_FOUND,
                    "Không tìm thấy công thức này trong sổ tay của bạn.");
        }

        customRecipeRepository.deleteByIdAndUserId(recipeId, user.getUserId());
        log.info("[COOKBOOK] User '{}' removed cookbook recipe ID: {}", user.getUsername(), recipeId);
    }

    private User resolveUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        try {
            UUID uid = UUID.fromString(identifier.trim());
            return userRepository.findById(uid)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        } catch (IllegalArgumentException ignored) {
            // Not a UUID -> lookup by username/email
        }
        return userRepository.findByUsernameOrEmailIgnoreCase(identifier.trim())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private CookbookRecipeResponse mapToResponse(CustomRecipe entity) {
        return CookbookRecipeResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .cookTimeMinutes(entity.getCookTimeMinutes())
                .difficulty(entity.getDifficulty())
                .estimatedCalories(entity.getEstimatedCalories())
                .dietaryType(entity.getDietaryType())
                .originSource(entity.getOriginSource())
                .ingredients(entity.getIngredients() != null ? new ArrayList<>(entity.getIngredients()) : List.of())
                .instructions(entity.getInstructions() != null ? new ArrayList<>(entity.getInstructions()) : List.of())
                .safetyNotes(entity.getSafetyNotes() != null ? new ArrayList<>(entity.getSafetyNotes()) : List.of())
                .sourceNotes(entity.getSourceNotes())
                .originRecipeId(entity.getOriginRecipeId())
                .imageUrl(entity.getImageUrl())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CookbookRecipeSummaryDto mapToSummaryDto(CustomRecipe entity) {
        return CookbookRecipeSummaryDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .cookTimeMinutes(entity.getCookTimeMinutes())
                .difficulty(entity.getDifficulty())
                .estimatedCalories(entity.getEstimatedCalories())
                .dietaryType(entity.getDietaryType())
                .originSource(entity.getOriginSource())
                .ingredientCount(entity.getIngredients() != null ? entity.getIngredients().size() : 0)
                .stepCount(entity.getInstructions() != null ? entity.getInstructions().size() : 0)
                .imageUrl(entity.getImageUrl())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
