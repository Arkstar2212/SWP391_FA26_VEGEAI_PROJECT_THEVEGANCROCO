package com.vegeai.backend.modules.fridge.repository;

import com.vegeai.backend.modules.fridge.entity.FreshnessStatus;
import com.vegeai.backend.modules.fridge.entity.FridgeItem;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FridgeItemRepository extends JpaRepository<FridgeItem, UUID> {

    /**
     * Tìm tất cả món của user theo trạng thái (mặc định ACTIVE), sắp xếp theo hạn sử dụng tăng dần (sắp hết hạn lên trước).
     */
    List<FridgeItem> findByUserIdAndStatusOrderByExpiryDateAsc(UUID userId, FridgeItemStatus status);

    /**
     * Tìm tất cả món của user bất kể trạng thái.
     */
    List<FridgeItem> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Tìm một món cụ thể đảm bảo thuộc quyền sở hữu của user.
     */
    Optional<FridgeItem> findByItemIdAndUserId(UUID itemId, UUID userId);

    /**
     * Tìm món đang còn trong tủ (ACTIVE) theo tên đã chuẩn hóa để phục vụ cơ chế "lưu nhớ & cập nhật" khi quét lại.
     */
    Optional<FridgeItem> findByUserIdAndNormalizedNameAndStatus(UUID userId, String normalizedName, FridgeItemStatus status);

    /**
     * Tìm các món sắp hết hạn (expiryDate <= thresholdDate) đang có trong tủ (ACTIVE).
     */
    List<FridgeItem> findByUserIdAndStatusAndExpiryDateLessThanEqualOrderByExpiryDateAsc(
            UUID userId, FridgeItemStatus status, LocalDate thresholdDate);

    /**
     * Đếm tổng số món của user theo trạng thái.
     */
    long countByUserIdAndStatus(UUID userId, FridgeItemStatus status);

    /**
     * Đếm số món theo trạng thái độ tươi.
     */
    long countByUserIdAndStatusAndFreshnessStatus(UUID userId, FridgeItemStatus status, FreshnessStatus freshnessStatus);

    /**
     * Đếm số món thuần chay đang có trong tủ.
     */
    long countByUserIdAndStatusAndIsVegetarian(UUID userId, FridgeItemStatus status, boolean isVegetarian);
}
