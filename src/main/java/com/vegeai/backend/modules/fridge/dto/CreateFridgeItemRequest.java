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
@Schema(description = "Yêu cầu thêm thủ công một nguyên liệu mới vào tủ lạnh")
public class CreateFridgeItemRequest {

    @NotBlank(message = "Tên nguyên liệu không được để trống")
    @Schema(description = "Tên nguyên liệu", example = "Nấm đùi gà")
    private String name;

    @Schema(description = "Phân loại chay: VEGAN, LACTO_OVO, NON_VEG", example = "VEGAN")
    @Builder.Default
    private String category = "VEGAN";

    @Schema(description = "Có phải đồ chay không", example = "true")
    @Builder.Default
    private Boolean isVegetarian = true;

    @Schema(description = "Số lượng", example = "300")
    private Double quantity;

    @Schema(description = "Đơn vị tính", example = "g")
    private String unit;

    @Schema(description = "Trạng thái tươi mới ban đầu: FRESH, USABLE, SPOILED", example = "FRESH")
    @Builder.Default
    private FreshnessStatus freshnessStatus = FreshnessStatus.FRESH;

    @Schema(description = "Số ngày bảo quản dự kiến (nếu không chọn ngày hết hạn)", example = "7")
    private Integer estimatedShelfLifeDays;

    @Schema(description = "Ngày hết hạn cụ thể (nếu có)", example = "2026-10-20")
    private LocalDate expiryDate;

    @Schema(description = "Mẹo bảo quản", example = "Bọc màng bọc thực phẩm ngăn mát")
    private String storageTip;

    @Schema(description = "Ghi chú cá nhân", example = "Mua ở siêu thị WinMart")
    private String notes;
}
