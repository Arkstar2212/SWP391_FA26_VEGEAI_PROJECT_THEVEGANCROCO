package com.vegeai.backend.modules.mealplan;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.*;
import java.util.UUID;
@Entity @Table(name = "meal_plans", indexes = @Index(name = "idx_meal_plans_owner", columnList = "user_id,status,created_at"))
@Getter @Setter
public class MealPlan {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 10) private String status;
    @Column(nullable = false, columnDefinition = "TEXT") private String snapshot;
    @Column(nullable = false, columnDefinition = "TEXT") private String profileSignature;
    @CreationTimestamp @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    private LocalDateTime savedAt;
    @Version private long version;
}
