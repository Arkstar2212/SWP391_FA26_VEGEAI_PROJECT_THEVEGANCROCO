package com.vegeai.backend.modules.mealplan;

import java.util.List;
import java.util.UUID;

public final class SeasonalMealDtos {
    private SeasonalMealDtos() {}

    public enum Season { XUAN, HA, THU, DONG }
    public enum Region { MIEN_BAC, MIEN_TRUNG, MIEN_NAM, MIEN_TAY }

    public record SeasonalMealSuggestion(
            String mealType,
            String dishName,
            String seasonalIngredients,
            String healthBenefits,
            String regionalHighlights,
            Integer estimatedCalories,
            String cookingTip,
            UUID matchedRecipeId
    ) {
        public SeasonalMealSuggestion(
                String mealType,
                String dishName,
                String seasonalIngredients,
                String healthBenefits,
                String regionalHighlights,
                Integer estimatedCalories,
                String cookingTip
        ) {
            this(mealType, dishName, seasonalIngredients, healthBenefits, regionalHighlights, estimatedCalories, cookingTip, null);
        }
    }

    public record SeasonalRegionalResponse(
            Season season,
            String seasonNameVi,
            Region region,
            String regionNameVi,
            String climateSummary,
            List<String> highlightedProduce,
            String nutritionAdvice,
            List<SeasonalMealSuggestion> suggestions,
            String disclaimer
    ) {
        public SeasonalRegionalResponse(
                Season season,
                String seasonNameVi,
                Region region,
                String regionNameVi,
                String climateSummary,
                List<String> highlightedProduce,
                String nutritionAdvice,
                List<SeasonalMealSuggestion> suggestions
        ) {
            this(season, seasonNameVi, region, regionNameVi, climateSummary, highlightedProduce, nutritionAdvice, suggestions,
                    "Gợi ý mang tính chất tham khảo dinh dưỡng theo mùa, không thay thế chẩn đoán hoặc phác đồ y khoa chuyên nghiệp.");
        }
    }
}
