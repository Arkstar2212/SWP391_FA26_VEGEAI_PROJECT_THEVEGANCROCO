package com.vegeai.backend.modules.mealplan;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
    Optional<MealPlan> findByIdAndUserId(UUID id, UUID userId);
    List<MealPlan> findByUserIdAndStatusOrderBySavedAtAscIdAsc(UUID userId, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MealPlan p where p.id=:id and p.userId=:userId")
    Optional<MealPlan> lockOwned(UUID id, UUID userId);
}
