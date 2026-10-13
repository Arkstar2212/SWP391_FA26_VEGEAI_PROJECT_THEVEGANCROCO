package com.vegeai.backend.modules.recipe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "recipe_ingredients", indexes = {
        @Index(name = "idx_recipe_ing_norm_name", columnList = "normalized_name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ingredient_id", updatable = false, nullable = false)
    private UUID ingredientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "normalized_name", length = 100, nullable = false)
    private String normalizedName;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "is_optional", nullable = false)
    @Builder.Default
    private boolean isOptional = false;

    @Column(name = "is_allergen", nullable = false)
    @Builder.Default
    private boolean isAllergen = false;

    @Column(name = "allergen_type", length = 50)
    private String allergenType;
}
