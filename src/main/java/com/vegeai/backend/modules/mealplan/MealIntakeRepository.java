package com.vegeai.backend.modules.mealplan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealIntakeRepository extends JpaRepository<MealIntake, UUID> {
    List<MealIntake> findByUserIdAndDateOrderBySlot(UUID userId, LocalDate date);
    Optional<MealIntake> findByUserIdAndDateAndSlot(UUID userId, LocalDate date, int slot);
}
