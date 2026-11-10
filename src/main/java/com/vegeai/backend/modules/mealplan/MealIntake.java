package com.vegeai.backend.modules.mealplan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A snapshot of what the customer reports eating, independent of later plan edits. */
@Entity
@Table(name = "meal_intakes", uniqueConstraints = @UniqueConstraint(
        name = "uk_meal_intakes_owner_day_slot", columnNames = {"user_id", "meal_date", "slot"}))
@Getter @Setter
public class MealIntake {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "meal_date", nullable = false) private LocalDate date;
    @Column(nullable = false) private int slot;
    @Column(name = "plan_id", nullable = false) private UUID planId;
    @Column(name = "recipe_id", nullable = false) private UUID recipeId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private double portions;
    @Column(nullable = false) private double calories;
    @Column(nullable = false) private double protein;
    @Column(nullable = false) private double carbs;
    @Column(nullable = false) private double fat;
    @Column(nullable = false) private double fiber;
    @Column(nullable = false) private LocalDateTime recordedAt;
    @Version private long version;
}
