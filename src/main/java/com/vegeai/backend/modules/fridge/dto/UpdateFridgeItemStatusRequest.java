package com.vegeai.backend.modules.fridge.dto;

import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu thay đổi trạng thái nguyên liệu (đã ăn, đã vứt hoặc quay lại hoạt động)")
public class UpdateFridgeItemStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    @Schema(description = "Trạng thái mới: ACTIVE (Còn trong tủ), CONSUMED (Đã ăn), DISCARDED (Đã vứt bỏ)", example = "CONSUMED")
    private FridgeItemStatus status;
}
