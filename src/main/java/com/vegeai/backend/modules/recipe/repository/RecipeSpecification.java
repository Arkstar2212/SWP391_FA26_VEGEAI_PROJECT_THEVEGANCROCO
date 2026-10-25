package com.vegeai.backend.modules.recipe.repository;

import com.vegeai.backend.modules.recipe.dto.RecipeFilterRequest;
import com.vegeai.backend.modules.recipe.entity.InclusionMode;
import com.vegeai.backend.modules.recipe.entity.Recipe;
import com.vegeai.backend.modules.recipe.entity.RecipeIngredient;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import com.vegeai.backend.modules.recipe.entity.Tag;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Xây dựng Specification cho Bộ lọc công thức món chay đa tiêu chí (Faceted Recipe Filter).
 * Hỗ trợ:
 *  - Lọc từ khóa (title / description)
 *  - Lọc ngưỡng dinh dưỡng (calo, đạm, thời gian nấu, độ khó)
 *  - Logic Inclusion Tags: AND (phải có đủ mọi tag) hoặc OR (có ít nhất 1 tag)
 *  - Logic Exclusion Tags: Tuyệt đối KHÔNG có bất kỳ tag nào trong danh sách loại trừ (dị ứng, kiêng cử)
 */
public class RecipeSpecification {

    public static Specification<Recipe> buildFilter(RecipeFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Chỉ lấy công thức đang PUBLISHED
            predicates.add(cb.equal(root.get("status"), RecipeStatus.PUBLISHED));

            // 2. Từ khóa tìm kiếm (Title hoặc Description)
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String pattern = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titleMatch, descMatch));
            }

            // 3. Thời gian nấu tối đa
            if (filter.getMaxCookTime() != null && filter.getMaxCookTime() > 0) {
                predicates.add(cb.lessThanOrEqualTo(root.get("cookTimeMinutes"), filter.getMaxCookTime()));
            }

            // 4. Mức calo tối đa
            if (filter.getMaxCalories() != null && filter.getMaxCalories() > 0) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calories"), filter.getMaxCalories()));
            }

            // 5. Lượng đạm tối thiểu
            if (filter.getMinProtein() != null && filter.getMinProtein() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("proteinGrams"), filter.getMinProtein()));
            }

            // 6. Độ khó
            if (filter.getDifficulty() != null) {
                predicates.add(cb.equal(root.get("difficulty"), filter.getDifficulty()));
            }

            // 7. EXCLUSION TAGS (Loại trừ tuyệt đối: Dị ứng, Thành phần kiêng & Thẻ phân loại)
            if (filter.getExcludeTagCodes() != null && !filter.getExcludeTagCodes().isEmpty()) {
                List<String> cleanExclude = filter.getExcludeTagCodes().stream()
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .filter(s -> !s.isEmpty())
                        .toList();

                if (!cleanExclude.isEmpty()) {
                    List<String> generalExclude = new ArrayList<>();

                    for (String code : cleanExclude) {
                        switch (code) {
                            case "SOY_FREE", "SOY", "CONTAINS_SOY" -> {
                                // Người dùng chọn loại trừ Đậu nành -> Loại bỏ món chứa đậu nành
                                Subquery<Long> soyIngSub = query.subquery(Long.class);
                                Root<RecipeIngredient> soyIngRoot = soyIngSub.from(RecipeIngredient.class);
                                soyIngSub.select(cb.literal(1L))
                                        .where(
                                                cb.equal(soyIngRoot.get("recipe").get("recipeId"), root.get("recipeId")),
                                                cb.or(
                                                        cb.equal(cb.upper(soyIngRoot.get("allergenType")), "SOY"),
                                                        cb.like(cb.lower(soyIngRoot.get("normalizedName")), "%dau phu%"),
                                                        cb.like(cb.lower(soyIngRoot.get("normalizedName")), "%dau hu%"),
                                                        cb.like(cb.lower(soyIngRoot.get("normalizedName")), "%dau nanh%"),
                                                        cb.like(cb.lower(soyIngRoot.get("normalizedName")), "%tofu%")
                                                )
                                        );
                                predicates.add(cb.not(cb.exists(soyIngSub)));
                            }
                            case "GLUTEN_FREE", "GLUTEN", "CONTAINS_GLUTEN" -> {
                                // Người dùng chọn loại trừ Gluten -> Loại bỏ món chứa gluten
                                Subquery<Long> glutenIngSub = query.subquery(Long.class);
                                Root<RecipeIngredient> glutenIngRoot = glutenIngSub.from(RecipeIngredient.class);
                                glutenIngSub.select(cb.literal(1L))
                                        .where(
                                                cb.equal(glutenIngRoot.get("recipe").get("recipeId"), root.get("recipeId")),
                                                cb.or(
                                                        cb.equal(cb.upper(glutenIngRoot.get("allergenType")), "GLUTEN"),
                                                        cb.like(cb.lower(glutenIngRoot.get("normalizedName")), "%bot mi%"),
                                                        cb.like(cb.lower(glutenIngRoot.get("normalizedName")), "%mi can%"),
                                                        cb.like(cb.lower(glutenIngRoot.get("normalizedName")), "%lua mi%"),
                                                        cb.like(cb.lower(glutenIngRoot.get("normalizedName")), "%seitan%")
                                                )
                                        );
                                predicates.add(cb.not(cb.exists(glutenIngSub)));
                            }
                            case "NUT_FREE", "NUT", "PEANUT", "PEANUTS", "CONTAINS_NUT" -> {
                                // Người dùng chọn loại trừ Đậu phộng & Các loại hạt -> Loại bỏ món chứa hạt
                                Subquery<Long> nutIngSub = query.subquery(Long.class);
                                Root<RecipeIngredient> nutIngRoot = nutIngSub.from(RecipeIngredient.class);
                                nutIngSub.select(cb.literal(1L))
                                        .where(
                                                cb.equal(nutIngRoot.get("recipe").get("recipeId"), root.get("recipeId")),
                                                cb.or(
                                                        cb.equal(cb.upper(nutIngRoot.get("allergenType")), "PEANUT"),
                                                        cb.equal(cb.upper(nutIngRoot.get("allergenType")), "PEANUTS"),
                                                        cb.equal(cb.upper(nutIngRoot.get("allergenType")), "NUT"),
                                                        cb.equal(cb.upper(nutIngRoot.get("allergenType")), "TREE_NUTS"),
                                                        cb.like(cb.lower(nutIngRoot.get("normalizedName")), "%dau phong%"),
                                                        cb.like(cb.lower(nutIngRoot.get("normalizedName")), "%lac%"),
                                                        cb.like(cb.lower(nutIngRoot.get("normalizedName")), "%hat dieu%"),
                                                        cb.like(cb.lower(nutIngRoot.get("normalizedName")), "%hanh nhan%"),
                                                        cb.like(cb.lower(nutIngRoot.get("normalizedName")), "%oc cho%")
                                                )
                                        );
                                predicates.add(cb.not(cb.exists(nutIngSub)));
                            }
                            case "MUSHROOM_FREE", "MUSHROOM", "MUSHROOMS", "CONTAINS_MUSHROOM" -> {
                                // Người dùng chọn loại trừ Nấm -> Loại bỏ món chứa nấm
                                Subquery<Long> mushroomIngSub = query.subquery(Long.class);
                                Root<RecipeIngredient> mushroomIngRoot = mushroomIngSub.from(RecipeIngredient.class);
                                mushroomIngSub.select(cb.literal(1L))
                                        .where(
                                                cb.equal(mushroomIngRoot.get("recipe").get("recipeId"), root.get("recipeId")),
                                                cb.or(
                                                        cb.equal(cb.upper(mushroomIngRoot.get("allergenType")), "MUSHROOM"),
                                                        cb.like(cb.lower(mushroomIngRoot.get("normalizedName")), "%nam%")
                                                )
                                        );
                                predicates.add(cb.not(cb.exists(mushroomIngSub)));
                            }
                            case "NOT_SPICY", "SPICY", "CAY" -> {
                                // Người dùng chọn loại trừ Cay -> Món phải có thẻ NOT_SPICY
                                Subquery<Long> spicyTagSub = query.subquery(Long.class);
                                Root<Recipe> spicyRoot = spicyTagSub.from(Recipe.class);
                                Join<Recipe, Tag> spicyTagJoin = spicyRoot.join("tags");
                                spicyTagSub.select(cb.literal(1L))
                                        .where(
                                                cb.equal(spicyRoot.get("recipeId"), root.get("recipeId")),
                                                cb.equal(spicyTagJoin.get("code"), "NOT_SPICY")
                                        );
                                predicates.add(cb.exists(spicyTagSub));
                            }
                            default -> generalExclude.add(code);
                        }
                    }

                    // Xử lý các thẻ phân loại thông thường khác (ví dụ: FRIED, HOTPOT, BRAISED...)
                    if (!generalExclude.isEmpty()) {
                        Subquery<Long> excludeSub = query.subquery(Long.class);
                        Root<Recipe> excludeRoot = excludeSub.from(Recipe.class);
                        Join<Recipe, Tag> excludeTagJoin = excludeRoot.join("tags");
                        excludeSub.select(cb.literal(1L))
                                .where(
                                        cb.equal(excludeRoot.get("recipeId"), root.get("recipeId")),
                                        excludeTagJoin.get("code").in(generalExclude)
                                );
                        predicates.add(cb.not(cb.exists(excludeSub)));
                    }
                }
            }

            // 8. INCLUSION TAGS (Logic AND / OR)
            if (filter.getIncludeTagCodes() != null && !filter.getIncludeTagCodes().isEmpty()) {
                List<String> cleanInclude = filter.getIncludeTagCodes().stream()
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .filter(s -> !s.isEmpty())
                        .toList();

                if (!cleanInclude.isEmpty()) {
                    if (filter.getInclusionMode() == InclusionMode.OR) {
                        // Chế độ OR: Chỉ cần có ÍT NHẤT 1 tag trong danh sách
                        Subquery<Long> orSub = query.subquery(Long.class);
                        Root<Recipe> orRoot = orSub.from(Recipe.class);
                        Join<Recipe, Tag> orTagJoin = orRoot.join("tags");
                        orSub.select(cb.literal(1L))
                                .where(
                                        cb.equal(orRoot.get("recipeId"), root.get("recipeId")),
                                        orTagJoin.get("code").in(cleanInclude)
                                );
                        predicates.add(cb.exists(orSub));
                    } else {
                        // Chế độ AND (Mặc định): Phải có TẤT CẢ các thẻ tag được chọn
                        for (String requiredTagCode : cleanInclude) {
                            Subquery<Long> andSub = query.subquery(Long.class);
                            Root<Recipe> andRoot = andSub.from(Recipe.class);
                            Join<Recipe, Tag> andTagJoin = andRoot.join("tags");
                            andSub.select(cb.literal(1L))
                                    .where(
                                            cb.equal(andRoot.get("recipeId"), root.get("recipeId")),
                                            cb.equal(andTagJoin.get("code"), requiredTagCode)
                                    );
                            predicates.add(cb.exists(andSub));
                        }
                    }
                }
            }

            // Đảm bảo không bị duplicate khi join
            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
