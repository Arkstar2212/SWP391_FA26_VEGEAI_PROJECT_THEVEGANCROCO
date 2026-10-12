package com.vegeai.backend.modules.fridge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả sau khi xác nhận lưu danh sách nguyên liệu từ kết quả quét vào kho tủ lạnh")
public class ConfirmFridgeScanResponse {

    @Schema(description = "Tổng số lượng nguyên liệu đã xử lý", example = "4")
    private int totalProcessed;

    @Schema(description = "Số lượng nguyên liệu mới tinh được thêm vào tủ", example = "3")
    private int newlyAddedCount;

    @Schema(description = "Số lượng nguyên liệu cũ đã có trong tủ được nhận diện lại và cập nhật vòng đời", example = "1")
    private int updatedExistingCount;

    @Schema(description = "Danh sách chi tiết các nguyên liệu trong tủ sau khi lưu")
    private List<FridgeItemResponse> items;

    @Schema(description = "Thông tin chi tiết ghi nhận từ cơ chế lưu nhớ (Memory Insights)", example = "[\"Cà chua: Phát hiện lần thứ 2, đã nằm trong tủ 3 ngày. Khuyến nghị ưu tiên dùng ngay!\"]")
    private List<String> memoryInsights;
}
