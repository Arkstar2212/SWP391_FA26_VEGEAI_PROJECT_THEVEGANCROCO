package com.vegeai.backend.modules.mealplan;

import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonalMealServiceTest {

    @Mock
    private RecipeRepository recipeRepository;

    private SeasonalMealService service;

    @BeforeEach
    void setUp() {
        service = new SeasonalMealService(recipeRepository);
    }

    @Test
    @DisplayName("Should return seasonal recommendations for North Region in Winter")
    void shouldReturnNorthWinterRecommendations() {
        when(recipeRepository.findByStatus(any())).thenReturn(Collections.emptyList());

        SeasonalMealDtos.SeasonalRegionalResponse response =
                service.getRecommendations(SeasonalMealDtos.Season.DONG, SeasonalMealDtos.Region.MIEN_BAC);

        assertThat(response).isNotNull();
        assertThat(response.season()).isEqualTo(SeasonalMealDtos.Season.DONG);
        assertThat(response.region()).isEqualTo(SeasonalMealDtos.Region.MIEN_BAC);
        assertThat(response.highlightedProduce()).isNotEmpty();
        assertThat(response.suggestions()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(response.disclaimer()).contains("tham khảo");
    }

    @Test
    @DisplayName("Should use sensible defaults when season or region is null")
    void shouldUseDefaultsWhenNull() {
        when(recipeRepository.findByStatus(any())).thenReturn(Collections.emptyList());

        SeasonalMealDtos.SeasonalRegionalResponse response =
                service.getRecommendations(null, null);

        assertThat(response).isNotNull();
        assertThat(response.season()).isNotNull();
        assertThat(response.region()).isEqualTo(SeasonalMealDtos.Region.MIEN_NAM);
        assertThat(response.suggestions()).isNotEmpty();
    }
}
