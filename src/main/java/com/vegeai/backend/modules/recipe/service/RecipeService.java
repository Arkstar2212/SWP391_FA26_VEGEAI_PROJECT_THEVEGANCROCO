package com.vegeai.backend.modules.recipe.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.fridge.entity.FridgeItem;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import com.vegeai.backend.modules.fridge.repository.FridgeItemRepository;
import com.vegeai.backend.modules.recipe.dto.*;
import com.vegeai.backend.modules.recipe.entity.*;
import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import com.vegeai.backend.modules.recipe.repository.RecipeSpecification;
import com.vegeai.backend.modules.recipe.repository.TagRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final TagRepository tagRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final UserRepository userRepository;
    private final TagService tagService;

    /**
     * Tìm kiếm và lọc công thức món chay đa tiêu chí (Faceted Tag Filter with AND/OR & Exclusion Logic).
     */
    @Transactional(readOnly = true)
    public PageResponse<RecipeSummaryDto> filterRecipes(RecipeFilterRequest filter) {
        Sort sort = resolveSort(filter.getSortBy(), filter.getSortDirection());
        PageRequest pageRequest = PageRequest.of(Math.max(0, filter.getPage()), Math.max(1, filter.getSize()), sort);

        Specification<Recipe> spec = RecipeSpecification.buildFilter(filter);
        Page<Recipe> recipePage = recipeRepository.findAll(spec, pageRequest);

        List<RecipeSummaryDto> dtos = recipePage.getContent().stream()
                .map(this::mapToSummaryDto)
                .toList();

        return PageResponse.from(recipePage, dtos);
    }

    /**
     * Xem chi tiết một công thức món ăn bằng ID.
     */
    @Transactional(readOnly = true)
    public RecipeDetailResponse getRecipeById(UUID recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPE_NOT_FOUND));
        return mapToDetailResponse(recipe);
    }

    /**
     * Xem chi tiết một công thức món ăn bằng SEO Slug.
     */
    @Transactional(readOnly = true)
    public RecipeDetailResponse getRecipeBySlug(String slug) {
        Recipe recipe = recipeRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPE_NOT_FOUND));
        return mapToDetailResponse(recipe);
    }

    /**
     * Thuật toán so khớp nguyên liệu tủ lạnh với kho công thức của Admin (Recipe Match Engine).
     * Trả về danh sách món ăn:
     *  - Khớp 100%: Đủ nguyên liệu nấu ngay.
     *  - Khớp >= 60%: Thiếu 1-2 nguyên liệu (gợi ý đi chợ).
     */
    @Transactional(readOnly = true)
    public FridgeRecipeMatchResponse matchRecipesWithFridge(String userIdStr, int minMatchPercentage, int limit) {
        User user = resolveUser(userIdStr);
        UUID userId = user.getUserId();

        // 1. Lấy danh sách nguyên liệu đang ACTIVE trong tủ lạnh
        List<FridgeItem> activeItems = fridgeItemRepository
                .findByUserIdAndStatusOrderByExpiryDateAsc(userId, FridgeItemStatus.ACTIVE);

        Set<String> fridgeNormNames = activeItems.stream()
                .map(FridgeItem::getNormalizedName)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        // 2. Lấy tất cả công thức đã PUBLISHED
        List<Recipe> publishedRecipes = recipeRepository.findByStatus(RecipeStatus.PUBLISHED);

        List<FridgeRecipeMatchDto> matchedResults = new ArrayList<>();
        int canCookNowCount = 0;
        int missingFewCount = 0;

        for (Recipe recipe : publishedRecipes) {
            List<RecipeIngredient> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty()) {
                continue;
            }

            // Lấy các nguyên liệu bắt buộc (isOptional = false)
            List<RecipeIngredient> mandatoryList = ingredients.stream()
                    .filter(i -> !i.isOptional())
                    .toList();

            // Nếu công thức toàn nguyên liệu tùy chọn, ta xét tất cả
            List<RecipeIngredient> targetIngredients = mandatoryList.isEmpty() ? ingredients : mandatoryList;
            int totalRequired = targetIngredients.size();

            int matchedCount = 0;
            List<String> available = new ArrayList<>();
            List<String> missing = new ArrayList<>();

            for (RecipeIngredient ing : targetIngredients) {
                String ingNorm = ing.getNormalizedName();
                boolean isFound = fridgeNormNames.stream()
                        .anyMatch(f -> f.equalsIgnoreCase(ingNorm) || f.contains(ingNorm) || ingNorm.contains(f));

                if (isFound) {
                    matchedCount++;
                    available.add(ing.getName());
                } else {
                    missing.add(ing.getName());
                }
            }

            int matchPercent = (totalRequired > 0)
                    ? (int) Math.min(100, Math.round(((double) matchedCount / totalRequired) * 100))
                    : 100;

            if (matchPercent >= Math.max(0, minMatchPercentage)) {
                MatchGroup group;
                String recText;

                if (matchPercent == 100) {
                    group = MatchGroup.CAN_COOK_NOW;
                    canCookNowCount++;
                    recText = "Bạn có đủ " + matchedCount + "/" + totalRequired + " nguyên liệu! Có thể nấu ngay hôm nay.";
                } else if (matchPercent >= 60) {
                    group = MatchGroup.MISSING_FEW;
                    missingFewCount++;
                    recText = "Khớp " + matchPercent + "%! Chỉ cần mua thêm: " + String.join(", ", missing);
                } else {
                    group = MatchGroup.INSUFFICIENT;
                    recText = "Còn thiếu " + missing.size() + " nguyên liệu: " + String.join(", ", missing);
                }

                matchedResults.add(FridgeRecipeMatchDto.builder()
                        .recipe(mapToSummaryDto(recipe))
                        .matchPercentage(matchPercent)
                        .matchGroup(group)
                        .totalMandatoryIngredients(totalRequired)
                        .matchedIngredientsCount(matchedCount)
                        .availableInFridge(available)
                        .missingIngredients(missing)
                        .recommendation(recText)
                        .build());
            }
        }

        // 3. Sắp xếp: Khớp 100% lên trước, sau đó tỷ lệ khớp giảm dần, ưu tiên thời gian nấu ngắn
        matchedResults.sort((a, b) -> {
            int cmp = Integer.compare(b.getMatchPercentage(), a.getMatchPercentage());
            if (cmp != 0) return cmp;
            Integer cookA = a.getRecipe().getCookTimeMinutes() != null ? a.getRecipe().getCookTimeMinutes() : 999;
            Integer cookB = b.getRecipe().getCookTimeMinutes() != null ? b.getRecipe().getCookTimeMinutes() : 999;
            return Integer.compare(cookA, cookB);
        });

        int maxLimit = Math.max(1, limit);
        List<FridgeRecipeMatchDto> limitedList = matchedResults.stream().limit(maxLimit).toList();

        return FridgeRecipeMatchResponse.builder()
                .totalRecipesEvaluated(publishedRecipes.size())
                .canCookNowCount(canCookNowCount)
                .missingFewCount(missingFewCount)
                .matchedRecipes(limitedList)
                .build();
    }

    /**
     * Tạo mới công thức món ăn chuẩn (Admin).
     */
    @Transactional
    public RecipeDetailResponse createRecipe(UUID authorId, CreateRecipeRequest request) {
        String slug = generateSlug(request.getTitle());

        Recipe recipe = Recipe.builder()
                .title(request.getTitle().trim())
                .slug(slug)
                .description(request.getDescription())
                .prepTimeMinutes(request.getPrepTimeMinutes())
                .cookTimeMinutes(request.getCookTimeMinutes())
                .servings(request.getServings())
                .difficulty(request.getDifficulty() != null ? request.getDifficulty() : DifficultyLevel.EASY)
                .calories(request.getCalories())
                .proteinGrams(request.getProteinGrams())
                .carbsGrams(request.getCarbsGrams())
                .fatGrams(request.getFatGrams())
                .fiberGrams(request.getFiberGrams())
                .imageUrl(request.getImageUrl())
                .instructions(request.getInstructions() != null ? new ArrayList<>(request.getInstructions()) : new ArrayList<>())
                .isVerified(true)
                .authorId(authorId)
                .status(request.getStatus() != null ? request.getStatus() : RecipeStatus.PUBLISHED)
                .build();

        // Xử lý Tags
        if (request.getTagCodes() != null && !request.getTagCodes().isEmpty()) {
            List<Tag> matchedTags = tagRepository.findByCodeIn(request.getTagCodes());
            recipe.setTags(new HashSet<>(matchedTags));
        }

        // Xử lý Nguyên liệu
        if (request.getIngredients() != null) {
            for (RecipeIngredientDto ingDto : request.getIngredients()) {
                RecipeIngredient ing = RecipeIngredient.builder()
                        .name(ingDto.getName().trim())
                        .normalizedName(normalizeName(ingDto.getName()))
                        .amount(ingDto.getAmount())
                        .unit(ingDto.getUnit())
                        .isOptional(ingDto.isOptional())
                        .isAllergen(ingDto.isAllergen())
                        .allergenType(ingDto.getAllergenType())
                        .build();
                recipe.addIngredient(ing);
            }
        }

        Recipe saved = recipeRepository.save(recipe);
        log.info("[RECIPE] Admin created recipe id='{}', title='{}'", saved.getRecipeId(), saved.getTitle());
        return mapToDetailResponse(saved);
    }

    /**
     * Cập nhật công thức món ăn (Admin).
     */
    @Transactional
    public RecipeDetailResponse updateRecipe(UUID recipeId, UpdateRecipeRequest request) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPE_NOT_FOUND));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            recipe.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            recipe.setDescription(request.getDescription());
        }
        if (request.getPrepTimeMinutes() != null) {
            recipe.setPrepTimeMinutes(request.getPrepTimeMinutes());
        }
        if (request.getCookTimeMinutes() != null) {
            recipe.setCookTimeMinutes(request.getCookTimeMinutes());
        }
        if (request.getServings() != null) {
            recipe.setServings(request.getServings());
        }
        if (request.getDifficulty() != null) {
            recipe.setDifficulty(request.getDifficulty());
        }
        if (request.getCalories() != null) {
            recipe.setCalories(request.getCalories());
        }
        if (request.getProteinGrams() != null) {
            recipe.setProteinGrams(request.getProteinGrams());
        }
        if (request.getCarbsGrams() != null) {
            recipe.setCarbsGrams(request.getCarbsGrams());
        }
        if (request.getFatGrams() != null) {
            recipe.setFatGrams(request.getFatGrams());
        }
        if (request.getFiberGrams() != null) {
            recipe.setFiberGrams(request.getFiberGrams());
        }
        if (request.getImageUrl() != null) {
            recipe.setImageUrl(request.getImageUrl());
        }
        if (request.getStatus() != null) {
            recipe.setStatus(request.getStatus());
        }
        if (request.getInstructions() != null) {
            recipe.setInstructions(new ArrayList<>(request.getInstructions()));
        }

        if (request.getTagCodes() != null) {
            List<Tag> matchedTags = tagRepository.findByCodeIn(request.getTagCodes());
            recipe.setTags(new HashSet<>(matchedTags));
        }

        if (request.getIngredients() != null) {
            recipe.clearIngredients();
            for (RecipeIngredientDto ingDto : request.getIngredients()) {
                RecipeIngredient ing = RecipeIngredient.builder()
                        .name(ingDto.getName().trim())
                        .normalizedName(normalizeName(ingDto.getName()))
                        .amount(ingDto.getAmount())
                        .unit(ingDto.getUnit())
                        .isOptional(ingDto.isOptional())
                        .isAllergen(ingDto.isAllergen())
                        .allergenType(ingDto.getAllergenType())
                        .build();
                recipe.addIngredient(ing);
            }
        }

        Recipe saved = recipeRepository.save(recipe);
        log.info("[RECIPE] Admin updated recipe id='{}', title='{}'", saved.getRecipeId(), saved.getTitle());
        return mapToDetailResponse(saved);
    }

    /**
     * Xóa công thức (Admin).
     */
    @Transactional
    public void deleteRecipe(UUID recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPE_NOT_FOUND));
        recipeRepository.delete(recipe);
        log.info("[RECIPE] Admin deleted recipe id='{}'", recipeId);
    }

    // ── Mapping & Sorting Utilities ──────────────────────────────────────────

    public RecipeSummaryDto mapToSummaryDto(Recipe recipe) {
        List<TagDto> tagDtos = recipe.getTags().stream()
                .map(tagService::mapToDto)
                .toList();

        return RecipeSummaryDto.builder()
                .recipeId(recipe.getRecipeId())
                .title(recipe.getTitle())
                .slug(recipe.getSlug())
                .imageUrl(recipe.getImageUrl())
                .cookTimeMinutes(recipe.getCookTimeMinutes())
                .difficulty(recipe.getDifficulty())
                .calories(recipe.getCalories())
                .proteinGrams(recipe.getProteinGrams())
                .isVerified(recipe.isVerified())
                .tags(tagDtos)
                .build();
    }

    public RecipeDetailResponse mapToDetailResponse(Recipe recipe) {
        List<TagDto> tagDtos = recipe.getTags().stream()
                .map(tagService::mapToDto)
                .toList();

        List<RecipeIngredientDto> ingDtos = recipe.getIngredients().stream()
                .map(i -> RecipeIngredientDto.builder()
                        .ingredientId(i.getIngredientId())
                        .name(i.getName())
                        .amount(i.getAmount())
                        .unit(i.getUnit())
                        .isOptional(i.isOptional())
                        .isAllergen(i.isAllergen())
                        .allergenType(i.getAllergenType())
                        .build())
                .toList();

        return RecipeDetailResponse.builder()
                .recipeId(recipe.getRecipeId())
                .title(recipe.getTitle())
                .slug(recipe.getSlug())
                .description(recipe.getDescription())
                .prepTimeMinutes(recipe.getPrepTimeMinutes())
                .cookTimeMinutes(recipe.getCookTimeMinutes())
                .servings(recipe.getServings())
                .difficulty(recipe.getDifficulty())
                .calories(recipe.getCalories())
                .proteinGrams(recipe.getProteinGrams())
                .carbsGrams(recipe.getCarbsGrams())
                .fatGrams(recipe.getFatGrams())
                .fiberGrams(recipe.getFiberGrams())
                .imageUrl(recipe.getImageUrl())
                .isVerified(recipe.isVerified())
                .status(recipe.getStatus())
                .ingredients(ingDtos)
                .instructions(new ArrayList<>(recipe.getInstructions()))
                .tags(tagDtos)
                .createdAt(recipe.getCreatedAt())
                .updatedAt(recipe.getUpdatedAt())
                .build();
    }

    public String normalizeName(String text) {
        if (text == null || text.isBlank()) return "";
        String norm = Normalizer.normalize(text.trim().toLowerCase(), Normalizer.Form.NFD);
        return norm.replaceAll("\\p{M}", "").replaceAll("\\s+", " ").trim();
    }

    private String generateSlug(String title) {
        if (title == null || title.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        String normalized = Normalizer.normalize(title.toLowerCase(), Normalizer.Form.NFD);
        String slug = normalized.replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            slug = "mon-chay";
        }
        if (recipeRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }

    private Sort resolveSort(String sortBy, String direction) {
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String prop = switch (sortBy != null ? sortBy.toLowerCase() : "newest") {
            case "cooktime" -> "cookTimeMinutes";
            case "calories" -> "calories";
            case "protein" -> "proteinGrams";
            default -> "createdAt";
        };
        return Sort.by(dir, prop);
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
            // Not a UUID -> try by username/email
        }
        return userRepository.findByUsernameOrEmailIgnoreCase(identifier.trim())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}
