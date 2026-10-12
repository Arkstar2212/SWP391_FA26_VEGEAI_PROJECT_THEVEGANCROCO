package com.vegeai.backend.modules.fridge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu xác nhận và lưu danh sách nguyên liệu vào tủ lạnh sau khi quét AI (User-in-the-Loop)")
public class ConfirmFridgeScanRequest {

    @NotEmpty(message = "Danh sách nguyên liệu xác nhận không được để trống")
    @Valid
    @Schema(description = "Danh sách các nguyên liệu người dùng đã duyệt và xác nhận")
    private List<ConfirmFridgeItemDto> items;
}
