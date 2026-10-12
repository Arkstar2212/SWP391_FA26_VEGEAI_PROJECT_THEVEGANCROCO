package com.vegeai.backend.modules.fridge.dto;

import com.vegeai.backend.modules.fridge.entity.FreshnessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin nguyên liệu sau khi người dùng kiểm duyệt từ kết quả scan để lưu vào tủ lạnh")
public class ConfirmFridgeItemDto {

    @NotBlank(message = "Tên nguyên liệu không được để trống")
    @Schema(description = "Tên nguyên liệu", example = "Cà chua")
    private String name;

    @Schema(description = "Phân loại chay: VEGAN, LACTO_OVO, NON_VEG", example = "VEGAN")
    private String category;

    @Schema(description = "Có phải đồ chay không", example = "true")
    @Builder.Default
    private Boolean isVegetarian = true;

    @Schema(description = "Số lượng", example = "4.0")
    private Double quantity;

    @Schema(description = "Đơn vị tính", example = "quả")
    private String unit;

    @Schema(description = "Trạng thái độ tươi: FRESH, USABLE, SPOILED", example = "FRESH")
    @Builder.Default
    private FreshnessStatus freshnessStatus = FreshnessStatus.FRESH;

    @Schema(description = "Điểm độ tươi từ AI (0.0 - 1.0)", example = "0.95")
    private Double freshnessScore;

    @Schema(description = "Số ngày bảo quản tối đa ước tính (VD: 5 ngày)", example = "5")
    private Integer estimatedShelfLifeDays;

    @Schema(description = "Ngày hết hạn (nếu không truyền sẽ tự động tính từ số ngày bảo quản)", example = "2026-10-18")
    private LocalDate expiryDate;

    @Schema(description = "Mẹo bảo quản", example = "Để nơi khô ráo, thoáng mát hoặc ngăn mát tủ lạnh")
    private String storageTip;

    @Schema(description = "Ghi chú thêm", example = "Đã rửa sạch")
    private String notes;

    @Schema(description = "Độ tin cậy của AI", example = "0.98")
    private Double confidence;
}
