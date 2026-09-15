package com.example.swp391_fa26_vegeai_project.modules.recipe.repository;

import com.example.swp391_fa26_vegeai_project.modules.recipe.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByDietaryType(String dietaryType);
}
