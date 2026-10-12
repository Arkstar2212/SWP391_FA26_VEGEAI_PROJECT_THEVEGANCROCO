package com.vegeai.backend.modules.fridge.dto;

import com.vegeai.backend.modules.fridge.entity.FreshnessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu chỉnh sửa thông tin nguyên liệu trong tủ lạnh")
public class UpdateFridgeItemRequest {

    @Schema(description = "Tên nguyên liệu mới", example = "Cà chua bi")
    private String name;

    @Schema(description = "Phân loại chay: VEGAN, LACTO_OVO, NON_VEG", example = "VEGAN")
    private String category;

    @Schema(description = "Có phải đồ chay không", example = "true")
    private Boolean isVegetarian;

    @Schema(description = "Số lượng", example = "500")
    private Double quantity;

    @Schema(description = "Đơn vị tính", example = "g")
    private String unit;

    @Schema(description = "Trạng thái độ tươi: FRESH, USABLE, SPOILED", example = "USABLE")
    private FreshnessStatus freshnessStatus;

    @Schema(description = "Ngày hết hạn mới", example = "2026-10-15")
    private LocalDate expiryDate;

    @Schema(description = "Mẹo bảo quản", example = "Để ngăn mát")
    private String storageTip;

    @Schema(description = "Ghi chú cá nhân", example = "Cần dùng trong 2 ngày")
    private String notes;
}
