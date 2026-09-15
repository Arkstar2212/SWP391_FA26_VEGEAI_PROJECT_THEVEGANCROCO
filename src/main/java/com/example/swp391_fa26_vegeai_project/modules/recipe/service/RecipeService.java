package com.example.swp391_fa26_vegeai_project.modules.recipe.service;

import com.example.swp391_fa26_vegeai_project.modules.recipe.entity.Recipe;
import com.example.swp391_fa26_vegeai_project.modules.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;

    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }
}
