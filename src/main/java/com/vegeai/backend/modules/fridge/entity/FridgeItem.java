package com.vegeai.backend.modules.fridge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Quản lý kho thực phẩm tủ lạnh thông minh (Smart Fridge Inventory Item).
 * Lưu vết vòng đời, đếm lùi hạn sử dụng và ghi nhớ lịch sử qua các lần quét AI.
 */
@Entity
@Table(name = "fridge_items", indexes = {
        @Index(name = "idx_fridge_user_status", columnList = "user_id, status"),
        @Index(name = "idx_fridge_user_norm_name", columnList = "user_id, normalized_name, status"),
        @Index(name = "idx_fridge_expiry", columnList = "expiry_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FridgeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id", updatable = false, nullable = false)
    private UUID itemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "normalized_name", length = 100, nullable = false)
    private String normalizedName;

    /**
     * Phân loại: VEGAN (Thuần chay), LACTO_OVO (Chay trứng sữa), NON_VEG (Đồ mặn)
     */
    @Column(name = "category", length = 30)
    @Builder.Default
    private String category = "VEGAN";

    @Column(name = "is_vegetarian", nullable = false)
    @Builder.Default
    private boolean isVegetarian = true;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "unit", length = 30)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "freshness_status", length = 20, nullable = false)
    @Builder.Default
    private FreshnessStatus freshnessStatus = FreshnessStatus.FRESH;

    @Column(name = "freshness_score")
    private Double freshnessScore;

    /**
     * Thời điểm lần đầu tiên nguyên liệu được phát hiện/đưa vào tủ lạnh.
     */
    @Column(name = "first_scanned_at", nullable = false)
    private LocalDateTime firstScannedAt;

    /**
     * Thời điểm lần gần nhất quét thấy hoặc người dùng cập nhật lại.
     */
    @Column(name = "last_scanned_at", nullable = false)
    private LocalDateTime lastScannedAt;

    /**
     * Số ngày bảo quản ước tính tối đa (VD: 5 ngày).
     */
    @Column(name = "estimated_shelf_life_days")
    private Integer estimatedShelfLifeDays;

    /**
     * Ngày hết hạn ước tính hoặc người dùng tự nhập.
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private FridgeItemStatus status = FridgeItemStatus.ACTIVE;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "storage_tip", length = 255)
    private String storageTip;

    @Column(name = "confidence")
    private Double confidence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Business Helper Methods ──────────────────────────────────────────────

    /**
     * Số ngày nguyên liệu đã nằm trong tủ lạnh tính từ lần đầu phát hiện.
     */
    public long calculateDaysInFridge() {
        if (firstScannedAt == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(firstScannedAt.toLocalDate(), LocalDate.now()));
    }

    /**
     * Số ngày còn lại trước khi hết hạn (số âm nghĩa là đã quá hạn).
     */
    public long calculateDaysRemaining() {
        if (expiryDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /**
     * Kiểm tra xem nguyên liệu có sắp hết hạn không (còn <= 2 ngày và đang ACTIVE).
     */
    public boolean isExpiringSoon() {
        return status == FridgeItemStatus.ACTIVE && calculateDaysRemaining() <= 2 && calculateDaysRemaining() >= 0;
    }

    /**
     * Kiểm tra xem nguyên liệu đã quá hạn sử dụng chưa.
     */
    public boolean isExpired() {
        return status == FridgeItemStatus.ACTIVE && calculateDaysRemaining() < 0;
    }
}
