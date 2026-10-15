package com.vegeai.backend.modules.recipe.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity lưu công thức món ăn cá nhân / do AI chế biến vào Sổ tay món ăn (Personal Cookbook / Custom Recipe).
 * Cho phép người dùng lưu lại các món đã chế biến từ quét tủ lạnh hoặc từ gợi ý của AI Chef
 * để xem lại mọi lúc mà không cần chụp lại tủ lạnh hay sinh lại từ đầu.
 */
@Entity
@Table(name = "custom_recipes", indexes = {
        @Index(name = "idx_custom_recipes_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_custom_recipes_user_title", columnList = "user_id, title")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "cook_time_minutes")
    private Integer cookTimeMinutes;

    @Column(name = "difficulty", length = 50)
    private String difficulty;

    @Column(name = "estimated_calories")
    private Integer estimatedCalories;

    @Column(name = "dietary_type", length = 50)
    private String dietaryType;

    /**
     * Nguồn gốc tạo công thức: AI_GENERATED, FRIDGE_SCAN, AI_CHEF, MANUAL, CLONED
     */
    @Column(name = "origin_source", length = 50)
    @Builder.Default
    private String originSource = "AI_GENERATED";

    /**
     * Danh sách nguyên liệu và định lượng chi tiết
     */
    @ElementCollection
    @CollectionTable(name = "custom_recipe_ingredients", joinColumns = @JoinColumn(name = "custom_recipe_id"))
    @Column(name = "ingredient_text", length = 500, nullable = false)
    @OrderColumn(name = "item_order")
    @Builder.Default
    private List<String> ingredients = new ArrayList<>();

    /**
     * Các bước hướng dẫn chế biến chi tiết
     */
    @ElementCollection
    @CollectionTable(name = "custom_recipe_instructions", joinColumns = @JoinColumn(name = "custom_recipe_id"))
    @Column(name = "instruction_text", length = 1500, nullable = false)
    @OrderColumn(name = "step_order")
    @Builder.Default
    private List<String> instructions = new ArrayList<>();

    /**
     * Lưu ý an toàn, lưu ý dị ứng hoặc mẹo dinh dưỡng
     */
    @ElementCollection
    @CollectionTable(name = "custom_recipe_safety_notes", joinColumns = @JoinColumn(name = "custom_recipe_id"))
    @Column(name = "note_text", length = 1000, nullable = false)
    @OrderColumn(name = "note_order")
    @Builder.Default
    private List<String> safetyNotes = new ArrayList<>();

    /**
     * Ghi chú khẩu vị hoặc kinh nghiệm nấu nướng riêng của người dùng
     */
    @Column(name = "source_notes", length = 1000)
    private String sourceNotes;

    /**
     * ID công thức mẫu chuẩn Admin nếu người dùng lưu từ kho công thức chung
     */
    @Column(name = "origin_recipe_id")
    private UUID originRecipeId;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
