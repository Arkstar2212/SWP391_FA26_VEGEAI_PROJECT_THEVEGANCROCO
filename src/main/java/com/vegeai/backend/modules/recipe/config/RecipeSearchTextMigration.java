package com.vegeai.backend.modules.recipe.config;

import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecipeSearchTextMigration implements ApplicationRunner {
    private final RecipeRepository recipes;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var stale = recipes.findAll().stream()
                .filter(recipe -> recipe.getSearchText() == null || recipe.getSearchText().isBlank())
                .peek(recipe -> recipe.refreshSearchText())
                .toList();
        if (!stale.isEmpty()) recipes.saveAll(stale);
    }
}
