package com.vegeai.backend.modules.fridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vegeai.backend.modules.fridge.entity.FreshnessStatus;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết một nguyên liệu trong kho tủ lạnh thông minh")
public class FridgeItemResponse {

    @Schema(description = "ID nguyên liệu", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID itemId;

    @Schema(description = "Tên nguyên liệu", example = "Cà chua")
    private String name;

    @Schema(description = "Phân loại chay: VEGAN, LACTO_OVO, NON_VEG", example = "VEGAN")
    private String category;

    @Schema(description = "Có phải đồ chay không", example = "true")
    @JsonProperty("isVegetarian")
    private boolean isVegetarian;

    @Schema(description = "Số lượng", example = "4.0")
    private Double quantity;

    @Schema(description = "Đơn vị tính", example = "quả")
    private String unit;

    @Schema(description = "Trạng thái độ tươi: FRESH, USABLE, SPOILED", example = "USABLE")
    private FreshnessStatus freshnessStatus;

    @Schema(description = "Điểm số độ tươi từ AI (0.0 - 1.0)", example = "0.85")
    private Double freshnessScore;

    @Schema(description = "Thời điểm lần đầu phát hiện trong tủ", example = "2026-10-09T10:30:00")
    private LocalDateTime firstScannedAt;

    @Schema(description = "Thời điểm lần gần nhất quét thấy hoặc cập nhật", example = "2026-10-12T08:15:00")
    private LocalDateTime lastScannedAt;

    @Schema(description = "Số ngày bảo quản ước tính tối đa", example = "5")
    private Integer estimatedShelfLifeDays;

    @Schema(description = "Ngày hết hạn", example = "2026-10-14")
    private LocalDate expiryDate;

    @Schema(description = "Số ngày nguyên liệu đã nằm trong tủ lạnh", example = "3")
    private long daysInFridge;

    @Schema(description = "Số ngày còn lại trước khi hết hạn (số âm = đã quá hạn)", example = "2")
    private long daysRemaining;

    @Schema(description = "Có sắp hết hạn trong 1-2 ngày tới không", example = "true")
    @JsonProperty("isExpiringSoon")
    private boolean isExpiringSoon;

    @Schema(description = "Đã quá hạn sử dụng hay chưa", example = "false")
    @JsonProperty("isExpired")
    private boolean isExpired;

    @Schema(description = "Trạng thái kho: ACTIVE (Còn trong tủ), CONSUMED (Đã ăn), DISCARDED (Đã vứt)", example = "ACTIVE")
    private FridgeItemStatus status;

    @Schema(description = "Mẹo bảo quản tối ưu", example = "Để nơi thoáng mát hoặc bọc túi zip trong ngăn rau")
    private String storageTip;

    @Schema(description = "Ghi chú cá nhân", example = "Ưu tiên nấu sốt cà chua hôm nay")
    private String notes;

    @Schema(description = "Độ tin cậy của AI", example = "0.96")
    private Double confidence;

    @Schema(description = "Cảnh báo khẩn cấp hoặc nhắc nhở từ AI", example = "⚠️ Cà chua đã nằm trong tủ 3 ngày (còn ~2 ngày nữa là hỏng). Hãy ưu tiên nấu ngay!")
    private String warningMessage;

    @Schema(description = "Thời điểm tạo bản ghi")
    private LocalDateTime createdAt;

    @Schema(description = "Thời điểm cập nhật bản ghi")
    private LocalDateTime updatedAt;
}
