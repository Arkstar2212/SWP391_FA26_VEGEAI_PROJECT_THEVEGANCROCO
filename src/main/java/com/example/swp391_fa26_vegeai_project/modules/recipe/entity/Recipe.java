package com.example.swp391_fa26_vegeai_project.modules.recipe.entity;

import com.example.swp391_fa26_vegeai_project.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "recipes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipe extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(length = 1500)
    private String description;

    private Integer cookTimeMinutes;
    private Integer calories;
    private String dietaryType; // VEGAN, VEGETARIAN
    private String difficulty;  // EASY, MEDIUM, HARD
    private String imageUrl;
    private Long authorId;
}
