package com.example.swp391_fa26_vegeai_project.modules.recipe.controller;

import com.example.swp391_fa26_vegeai_project.common.dto.ApiResponse;
import com.example.swp391_fa26_vegeai_project.modules.recipe.entity.Recipe;
import com.example.swp391_fa26_vegeai_project.modules.recipe.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Recipe>>> getAllRecipes() {
        return ResponseEntity.ok(ApiResponse.ok("Recipes retrieved", recipeService.getAllRecipes()));
    }
}
