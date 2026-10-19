package com.vegeai.backend.modules.recipe.dto;

import com.vegeai.backend.modules.recipe.entity.DifficultyLevel;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AdminRecipeSummaryDto {
    private UUID recipeId;
    private String title;
    private String slug;
    private String imageUrl;
    private Integer cookTimeMinutes;
    private DifficultyLevel difficulty;
    private Integer calories;
    private Double proteinGrams;
    private RecipeStatus status;
    private List<TagDto> tags;
}
