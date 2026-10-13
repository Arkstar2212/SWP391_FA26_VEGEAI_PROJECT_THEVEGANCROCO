package com.vegeai.backend.modules.recipe.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "recipes", indexes = {
        @Index(name = "idx_recipes_slug", columnList = "slug", unique = true),
        @Index(name = "idx_recipes_status", columnList = "status"),
        @Index(name = "idx_recipes_calories", columnList = "calories"),
        @Index(name = "idx_recipes_cook_time", columnList = "cook_time_minutes")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recipe_id", updatable = false, nullable = false)
    private UUID recipeId;

    @Column(name = "title", length = 150, nullable = false)
    private String title;

    @Column(name = "slug", length = 160, nullable = false, unique = true)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "cook_time_minutes")
    private Integer cookTimeMinutes;

    @Column(name = "prep_time_minutes")
    private Integer prepTimeMinutes;

    @Column(name = "servings")
    private Integer servings;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 20)
    @Builder.Default
    private DifficultyLevel difficulty = DifficultyLevel.EASY;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "protein_grams")
    private Double proteinGrams;

    @Column(name = "carbs_grams")
    private Double carbsGrams;

    @Column(name = "fat_grams")
    private Double fatGrams;

    @Column(name = "fiber_grams")
    private Double fiberGrams;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipe_instructions", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "step_description", length = 1000, nullable = false)
    @OrderColumn(name = "step_order")
    @Builder.Default
    private List<String> instructions = new ArrayList<>();

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean isVerified = true;

    @Column(name = "author_id")
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private RecipeStatus status = RecipeStatus.PUBLISHED;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "recipe_tags",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addIngredient(RecipeIngredient ingredient) {
        ingredients.add(ingredient);
        ingredient.setRecipe(this);
    }

    public void clearIngredients() {
        ingredients.forEach(i -> i.setRecipe(null));
        ingredients.clear();
    }
}
