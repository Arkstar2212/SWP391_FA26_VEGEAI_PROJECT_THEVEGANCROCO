package com.vegeai.backend.modules.fridge.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.fridge.dto.*;
import com.vegeai.backend.modules.fridge.entity.FreshnessStatus;
import com.vegeai.backend.modules.fridge.entity.FridgeItem;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import com.vegeai.backend.modules.fridge.repository.FridgeItemRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FridgeService {

    private final FridgeItemRepository fridgeItemRepository;
    private final UserRepository userRepository;

    /**
     * Xác nhận danh sách nguyên liệu sau khi quét AI (User-in-the-Loop).
     * Áp dụng cơ chế "Smart Fridge Memory":
     * - Nếu nguyên liệu đã có sẵn trong tủ: cập nhật lastScannedAt, tính số ngày nằm trong tủ và đếm lùi hạn dùng.
     * - Nếu nguyên liệu mới: tạo bản ghi mới với firstScannedAt = now.
     */
    @Transactional
    public ConfirmFridgeScanResponse confirmScanItems(String userIdStr, ConfirmFridgeScanRequest request) {
        User user = resolveUser(userIdStr);
        UUID userId = user.getUserId();

        List<FridgeItem> processedItems = new ArrayList<>();
        List<String> insights = new ArrayList<>();
        int newlyAdded = 0;
        int updatedExisting = 0;

        for (ConfirmFridgeItemDto dto : request.getItems()) {
            String normName = normalizeName(dto.getName());

            Optional<FridgeItem> existingOpt = fridgeItemRepository
                    .findByUserIdAndNormalizedNameAndStatus(userId, normName, FridgeItemStatus.ACTIVE);

            if (existingOpt.isPresent()) {
                // Đã có trong tủ -> Quét lại lần sau: Cập nhật vòng đời & hạn dùng
                FridgeItem existing = existingOpt.get();
                existing.setLastScannedAt(LocalDateTime.now());

                if (dto.getFreshnessStatus() != null) {
                    existing.setFreshnessStatus(dto.getFreshnessStatus());
                }
                if (dto.getFreshnessScore() != null) {
                    existing.setFreshnessScore(dto.getFreshnessScore());
                }
                if (dto.getQuantity() != null) {
                    existing.setQuantity(dto.getQuantity());
                }
                if (dto.getUnit() != null && !dto.getUnit().isBlank()) {
                    existing.setUnit(dto.getUnit());
                }
                if (dto.getStorageTip() != null && !dto.getStorageTip().isBlank()) {
                    existing.setStorageTip(dto.getStorageTip());
                }
                if (dto.getConfidence() != null) {
                    existing.setConfidence(dto.getConfidence());
                }
                if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
                    existing.setNotes(dto.getNotes());
                }

                // Tự động kiểm tra: nếu ngày còn lại <= 1 mà vẫn để FRESH thì chuyển sang USABLE
                long daysRemaining = existing.calculateDaysRemaining();
                long daysInFridge = existing.calculateDaysInFridge();
                if (daysRemaining <= 1 && existing.getFreshnessStatus() == FreshnessStatus.FRESH) {
                    existing.setFreshnessStatus(FreshnessStatus.USABLE);
                } else if (daysRemaining < 0) {
                    existing.setFreshnessStatus(FreshnessStatus.SPOILED);
                }

                FridgeItem saved = fridgeItemRepository.save(existing);
                processedItems.add(saved);
                updatedExisting++;

                insights.add("'" + saved.getName() + "': Nhận diện lại sau " + daysInFridge
                        + " ngày nằm trong tủ (còn ~" + daysRemaining + " ngày hạn dùng).");
            } else {
                // Chưa có trong tủ -> Thêm mới vào kho
                int shelfLifeDays = (dto.getEstimatedShelfLifeDays() != null && dto.getEstimatedShelfLifeDays() > 0)
                        ? dto.getEstimatedShelfLifeDays()
                        : 5;

                LocalDate expiry = (dto.getExpiryDate() != null)
                        ? dto.getExpiryDate()
                        : LocalDate.now().plusDays(shelfLifeDays);

                FridgeItem newItem = FridgeItem.builder()
                        .userId(userId)
                        .name(dto.getName().trim())
                        .normalizedName(normName)
                        .category(dto.getCategory() != null ? dto.getCategory() : "VEGAN")
                        .isVegetarian(dto.getIsVegetarian() == null || dto.getIsVegetarian())
                        .quantity(dto.getQuantity())
                        .unit(dto.getUnit())
                        .freshnessStatus(dto.getFreshnessStatus() != null ? dto.getFreshnessStatus() : FreshnessStatus.FRESH)
                        .freshnessScore(dto.getFreshnessScore())
                        .firstScannedAt(LocalDateTime.now())
                        .lastScannedAt(LocalDateTime.now())
                        .estimatedShelfLifeDays(shelfLifeDays)
                        .expiryDate(expiry)
                        .status(FridgeItemStatus.ACTIVE)
                        .storageTip(dto.getStorageTip())
                        .notes(dto.getNotes())
                        .confidence(dto.getConfidence())
                        .build();

                FridgeItem saved = fridgeItemRepository.save(newItem);
                processedItems.add(saved);
                newlyAdded++;

                insights.add("Thêm mới '" + saved.getName() + "' vào tủ lạnh (hạn sử dụng ước tính đến: " + expiry + ").");
            }
        }

        List<FridgeItemResponse> responses = processedItems.stream()
                .map(this::mapToResponse)
                .toList();

        return ConfirmFridgeScanResponse.builder()
                .totalProcessed(processedItems.size())
                .newlyAddedCount(newlyAdded)
                .updatedExistingCount(updatedExisting)
                .items(responses)
                .memoryInsights(insights)
                .build();
    }

    /**
     * Thêm thủ công 1 nguyên liệu mới vào tủ lạnh.
     */
    @Transactional
    public FridgeItemResponse addManualItem(String userIdStr, CreateFridgeItemRequest request) {
        User user = resolveUser(userIdStr);
        String normName = normalizeName(request.getName());

        int shelfLifeDays = (request.getEstimatedShelfLifeDays() != null && request.getEstimatedShelfLifeDays() > 0)
                ? request.getEstimatedShelfLifeDays()
                : 5;

        LocalDate expiry = (request.getExpiryDate() != null)
                ? request.getExpiryDate()
                : LocalDate.now().plusDays(shelfLifeDays);

        FridgeItem item = FridgeItem.builder()
                .userId(user.getUserId())
                .name(request.getName().trim())
                .normalizedName(normName)
                .category(request.getCategory() != null ? request.getCategory() : "VEGAN")
                .isVegetarian(request.getIsVegetarian() == null || request.getIsVegetarian())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .freshnessStatus(request.getFreshnessStatus() != null ? request.getFreshnessStatus() : FreshnessStatus.FRESH)
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .estimatedShelfLifeDays(shelfLifeDays)
                .expiryDate(expiry)
                .status(FridgeItemStatus.ACTIVE)
                .storageTip(request.getStorageTip())
                .notes(request.getNotes())
                .build();

        FridgeItem saved = fridgeItemRepository.save(item);
        return mapToResponse(saved);
    }

    /**
     * Lấy danh sách nguyên liệu trong tủ của người dùng.
     * Mặc định lấy các món đang có trong tủ (ACTIVE), sắp xếp theo hạn sử dụng tăng dần (món sắp hỏng lên trước).
     */
    @Transactional(readOnly = true)
    public List<FridgeItemResponse> getMyFridgeItems(String userIdStr, FridgeItemStatus status) {
        User user = resolveUser(userIdStr);
        FridgeItemStatus targetStatus = (status != null) ? status : FridgeItemStatus.ACTIVE;

        List<FridgeItem> items = fridgeItemRepository
                .findByUserIdAndStatusOrderByExpiryDateAsc(user.getUserId(), targetStatus);

        return items.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Xem chi tiết một nguyên liệu trong tủ lạnh.
     */
    @Transactional(readOnly = true)
    public FridgeItemResponse getFridgeItemById(String userIdStr, UUID itemId) {
        User user = resolveUser(userIdStr);
        FridgeItem item = fridgeItemRepository.findByItemIdAndUserId(itemId, user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));
        return mapToResponse(item);
    }

    /**
     * Cập nhật thông tin chi tiết một nguyên liệu.
     */
    @Transactional
    public FridgeItemResponse updateItem(String userIdStr, UUID itemId, UpdateFridgeItemRequest request) {
        User user = resolveUser(userIdStr);
        FridgeItem item = fridgeItemRepository.findByItemIdAndUserId(itemId, user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));

        if (request.getName() != null && !request.getName().isBlank()) {
            item.setName(request.getName().trim());
            item.setNormalizedName(normalizeName(request.getName()));
        }
        if (request.getCategory() != null) {
            item.setCategory(request.getCategory());
        }
        if (request.getIsVegetarian() != null) {
            item.setVegetarian(request.getIsVegetarian());
        }
        if (request.getQuantity() != null) {
            item.setQuantity(request.getQuantity());
        }
        if (request.getUnit() != null) {
            item.setUnit(request.getUnit());
        }
        if (request.getFreshnessStatus() != null) {
            item.setFreshnessStatus(request.getFreshnessStatus());
        }
        if (request.getExpiryDate() != null) {
            item.setExpiryDate(request.getExpiryDate());
        }
        if (request.getStorageTip() != null) {
            item.setStorageTip(request.getStorageTip());
        }
        if (request.getNotes() != null) {
            item.setNotes(request.getNotes());
        }

        FridgeItem saved = fridgeItemRepository.save(item);
        return mapToResponse(saved);
    }

    /**
     * Đổi trạng thái món ăn (ACTIVE -> CONSUMED / DISCARDED).
     */
    @Transactional
    public FridgeItemResponse updateItemStatus(String userIdStr, UUID itemId, FridgeItemStatus newStatus) {
        User user = resolveUser(userIdStr);
        FridgeItem item = fridgeItemRepository.findByItemIdAndUserId(itemId, user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));

        item.setStatus(newStatus);
        FridgeItem saved = fridgeItemRepository.save(item);
        return mapToResponse(saved);
    }

    /**
     * Xóa hoàn toàn một nguyên liệu khỏi cơ sở dữ liệu.
     */
    @Transactional
    public void deleteItem(String userIdStr, UUID itemId) {
        User user = resolveUser(userIdStr);
        FridgeItem item = fridgeItemRepository.findByItemIdAndUserId(itemId, user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));

        fridgeItemRepository.delete(item);
    }

    /**
     * Lấy danh sách các món sắp hết hạn (trong vòng N ngày, mặc định 2 ngày) hoặc đã quá hạn.
     */
    @Transactional(readOnly = true)
    public List<FridgeItemResponse> getExpiringSoonItems(String userIdStr, int daysThreshold) {
        User user = resolveUser(userIdStr);
        LocalDate threshold = LocalDate.now().plusDays(Math.max(1, daysThreshold));

        List<FridgeItem> items = fridgeItemRepository
                .findByUserIdAndStatusAndExpiryDateLessThanEqualOrderByExpiryDateAsc(
                        user.getUserId(), FridgeItemStatus.ACTIVE, threshold);

        return items.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Báo cáo thống kê tổng quan tủ lạnh và cảnh báo khẩn cấp.
     */
    @Transactional(readOnly = true)
    public FridgeSummaryResponse getFridgeSummary(String userIdStr) {
        User user = resolveUser(userIdStr);
        UUID userId = user.getUserId();

        List<FridgeItem> activeItems = fridgeItemRepository
                .findByUserIdAndStatusOrderByExpiryDateAsc(userId, FridgeItemStatus.ACTIVE);

        int totalActive = activeItems.size();
        int freshCount = 0;
        int usableCount = 0;
        int expiringSoonCount = 0;
        int expiredCount = 0;
        int veganCount = 0;

        List<String> urgentWarnings = new ArrayList<>();

        for (FridgeItem item : activeItems) {
            long remaining = item.calculateDaysRemaining();
            if (item.getFreshnessStatus() == FreshnessStatus.FRESH) {
                freshCount++;
            } else if (item.getFreshnessStatus() == FreshnessStatus.USABLE) {
                usableCount++;
            }

            if (item.isVegetarian()) {
                veganCount++;
            }

            if (remaining < 0) {
                expiredCount++;
            } else if (remaining <= 2) {
                expiringSoonCount++;
            }
        }

        if (expiredCount > 0) {
            urgentWarnings.add("⚠️ Có " + expiredCount + " món trong tủ đã quá hạn sử dụng. Hãy kiểm tra và xử lý để đảm bảo vệ sinh an toàn!");
        }
        if (expiringSoonCount > 0) {
            urgentWarnings.add("⏰ Có " + expiringSoonCount + " món sắp hết hạn trong vòng 48h tới. Hãy ưu tiên nấu ngay để tránh lãng phí!");
        }

        return FridgeSummaryResponse.builder()
                .totalActiveItems(totalActive)
                .freshCount(freshCount)
                .usableCount(usableCount)
                .expiringSoonCount(expiringSoonCount)
                .expiredCount(expiredCount)
                .veganCount(veganCount)
                .urgentWarnings(urgentWarnings)
                .build();
    }

    // ── Mapping & Normalization Utilities ────────────────────────────────────

    public FridgeItemResponse mapToResponse(FridgeItem item) {
        long daysInFridge = item.calculateDaysInFridge();
        long daysRemaining = item.calculateDaysRemaining();
        boolean expiringSoon = item.isExpiringSoon();
        boolean expired = item.isExpired();

        String warningMessage = null;
        if (expired) {
            warningMessage = "⚠️ Đã quá hạn " + Math.abs(daysRemaining) + " ngày! Vui lòng kiểm tra kỹ hoặc loại bỏ để đảm bảo an toàn.";
        } else if (expiringSoon) {
            warningMessage = "⚠️ Đã nằm trong tủ " + daysInFridge + " ngày (còn ~" + daysRemaining + " ngày hạn dùng). Ưu tiên nấu ngay!";
        } else if (daysInFridge >= 3) {
            warningMessage = "ℹ️ Đã nằm trong tủ " + daysInFridge + " ngày. Vẫn trong hạn dùng (" + daysRemaining + " ngày nữa).";
        }

        return FridgeItemResponse.builder()
                .itemId(item.getItemId())
                .name(item.getName())
                .category(item.getCategory())
                .isVegetarian(item.isVegetarian())
                .quantity(item.getQuantity())
                .unit(item.getUnit())
                .freshnessStatus(item.getFreshnessStatus())
                .freshnessScore(item.getFreshnessScore())
                .firstScannedAt(item.getFirstScannedAt())
                .lastScannedAt(item.getLastScannedAt())
                .estimatedShelfLifeDays(item.getEstimatedShelfLifeDays())
                .expiryDate(item.getExpiryDate())
                .daysInFridge(daysInFridge)
                .daysRemaining(daysRemaining)
                .isExpiringSoon(expiringSoon)
                .isExpired(expired)
                .status(item.getStatus())
                .storageTip(item.getStorageTip())
                .notes(item.getNotes())
                .confidence(item.getConfidence())
                .warningMessage(warningMessage)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    /**
     * Chuẩn hóa tên nguyên liệu: bỏ dấu tiếng Việt, viết thường, cắt khoảng trắng dư thừa
     * Giúp "Cà chua", "cà chua", "Ca chua" đều khớp về "ca chua".
     */
    public String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(name.trim().toLowerCase(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replaceAll("\\s+", " ").trim();
    }

    private User resolveUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        try {
            UUID uid = UUID.fromString(identifier.trim());
            return userRepository.findById(uid)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        } catch (IllegalArgumentException ignored) {
            // Not a UUID -> try by username/email
        }
        return userRepository.findByUsernameOrEmailIgnoreCase(identifier.trim())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}
