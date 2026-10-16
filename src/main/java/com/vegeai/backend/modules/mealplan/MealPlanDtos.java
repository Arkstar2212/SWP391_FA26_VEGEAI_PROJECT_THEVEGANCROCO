package com.vegeai.backend.modules.mealplan;
import com.vegeai.backend.modules.recipe.dto.RecipeDetailResponse;
import com.vegeai.backend.modules.nutrition.dto.NutritionTargetsResponse;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class MealPlanDtos {
    private MealPlanDtos() {}
    public record Generate(@NotNull @Pattern(regexp="WEEK|MONTH") String period, @NotNull LocalDate startDate,
                           @Min(1) @Max(6) int mealsPerDay) {}
    public record Swap(@NotNull UUID recipeId) {}
    public record Nutrients(double calories, double protein, double carbs, double fat, double fiber) {}
    public record Meal(int slot, UUID recipeId, String title, String imageUrl, double portions,
                       Nutrients nutrition, RecipeDetailResponse recipe) {}
    public record Day(LocalDate date, List<Meal> meals, Nutrients totals) {}
    public record Snapshot(String period, LocalDate startDate, LocalDate endDate, int mealsPerDay,
                           NutritionTargetsResponse targets, List<Day> days, List<String> warnings) {}
    public record View(UUID id, String status, LocalDateTime createdAt, LocalDateTime savedAt, Snapshot plan) {}
    public record Summary(UUID id, String period, LocalDate startDate, LocalDate endDate, int mealsPerDay, LocalDateTime savedAt) {}
}
