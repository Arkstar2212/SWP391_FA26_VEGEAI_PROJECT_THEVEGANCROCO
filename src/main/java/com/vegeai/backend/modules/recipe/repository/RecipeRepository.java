package com.vegeai.backend.modules.recipe.repository;

import com.vegeai.backend.modules.recipe.entity.Recipe;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID>, JpaSpecificationExecutor<Recipe> {

    Optional<Recipe> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Recipe> findByStatus(RecipeStatus status);

    long countByStatus(RecipeStatus status);
}
