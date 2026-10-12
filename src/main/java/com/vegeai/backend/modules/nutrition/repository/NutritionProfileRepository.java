package com.vegeai.backend.modules.nutrition.repository;

import com.vegeai.backend.modules.nutrition.entity.NutritionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NutritionProfileRepository extends JpaRepository<NutritionProfile, UUID> {

    Optional<NutritionProfile> findByUser_UserId(UUID userId);

    boolean existsByUser_UserId(UUID userId);

    void deleteByUser_UserId(UUID userId);
}
