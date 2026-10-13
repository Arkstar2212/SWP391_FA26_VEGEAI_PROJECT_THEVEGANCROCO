package com.vegeai.backend.modules.recipe.repository;

import com.vegeai.backend.modules.recipe.dto.RecipeFilterRequest;
import com.vegeai.backend.modules.recipe.entity.InclusionMode;
import com.vegeai.backend.modules.recipe.entity.Recipe;
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

            // 7. EXCLUSION TAGS (Loại trừ tuyệt đối: Dị ứng, Thành phần kiêng)
            if (filter.getExcludeTagCodes() != null && !filter.getExcludeTagCodes().isEmpty()) {
                List<String> cleanExclude = filter.getExcludeTagCodes().stream()
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .filter(s -> !s.isEmpty())
                        .toList();

                if (!cleanExclude.isEmpty()) {
                    Subquery<Long> excludeSub = query.subquery(Long.class);
                    Root<Recipe> excludeRoot = excludeSub.from(Recipe.class);
                    Join<Recipe, Tag> excludeTagJoin = excludeRoot.join("tags");
                    excludeSub.select(cb.literal(1L))
                            .where(
                                    cb.equal(excludeRoot.get("recipeId"), root.get("recipeId")),
                                    excludeTagJoin.get("code").in(cleanExclude)
                            );
                    predicates.add(cb.not(cb.exists(excludeSub)));
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
