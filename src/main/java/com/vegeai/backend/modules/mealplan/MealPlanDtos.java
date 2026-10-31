package com.vegeai.backend.modules.mealplan;
import com.vegeai.backend.modules.recipe.dto.RecipeDetailResponse;
import com.vegeai.backend.modules.nutrition.dto.NutritionTargetsResponse;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class MealPlanDtos {
    private MealPlanDtos() {}
    public record Generate(@NotNull @Pattern(regexp="WEEK|MONTH|CUSTOM") String period, @NotNull LocalDate startDate,
                           @Min(1) @Max(6) int mealsPerDay, @Min(1) @Max(31) Integer days) {}
    public record Swap(@NotNull UUID recipeId) {}
    public record Nutrients(double calories, double protein, double carbs, double fat, double fiber) {}
    public record Meal(int slot, UUID recipeId, String title, String imageUrl, double portions,
                       Nutrients nutrition, RecipeDetailResponse recipe) {}
    public record Day(LocalDate date, List<Meal> meals, Nutrients totals) {}
    public record Snapshot(String period, LocalDate startDate, LocalDate endDate, int mealsPerDay,
                           NutritionTargetsResponse targets, List<Day> days, List<String> warnings) {}
    public record View(UUID id, String status, LocalDateTime createdAt, LocalDateTime savedAt, Snapshot plan) {}
    /** visibleDays/firstVisibleDate/lastVisibleDate: days of this plan not overridden by a plan saved later. */
    public record Summary(UUID id, String period, LocalDate startDate, LocalDate endDate, int mealsPerDay, LocalDateTime savedAt,
                          int totalDays, int visibleDays, LocalDate firstVisibleDate, LocalDate lastVisibleDate) {}
    public record CalendarDay(LocalDate date, UUID planId, String period, NutritionTargetsResponse targets, List<Meal> meals, Nutrients totals) {}
}
