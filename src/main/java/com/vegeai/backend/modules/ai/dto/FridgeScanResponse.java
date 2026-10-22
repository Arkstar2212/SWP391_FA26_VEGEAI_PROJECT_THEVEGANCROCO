package com.vegeai.backend.modules.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả phân tích hình ảnh tủ lạnh và giám định độ tươi")
public class FridgeScanResponse {

    @Schema(description = "Tóm tắt kết quả phân tích tủ lạnh", example = "Phát hiện 4 loại nguyên liệu thực vật tươi mới, không có đồ mặn.")
    private String summary;

    @Schema(description = "Tổng số lượng nguyên liệu phát hiện được trong ảnh", example = "4")
    private int totalItemsDetected;

    @Schema(description = "Cảnh báo nếu phát hiện thực phẩm có nguồn gốc từ thịt động vật/hải sản", example = "false")
    private boolean hasNonVegetarianItem;

    @Schema(description = "Danh sách chi tiết từng nguyên liệu phát hiện được")
    private List<ScannedIngredient> ingredients;

    @Schema(description = "Gợi ý các món chay có thể nấu ngay từ các nguyên liệu còn dùng được", example = "[\"Canh cải ngọt nấu nấm\", \"Cà chua sốt đậu phụ giòn\"]")
    private List<String> suggestedRecipes;

    @Schema(description = "Cảnh báo an toàn thực phẩm, nguyên liệu hỏng hoặc cảnh báo dị ứng nếu có")
    private List<String> warnings;

    @Schema(description = "Thời điểm quét ảnh")
    private Instant scannedAt;

    @Schema(description = "Cờ báo người dùng cần hoàn tất thiết lập hồ sơ dinh dưỡng & sở thích ăn chay trước khi bấm Chế biến (Cook Now)", example = "false")
    @com.fasterxml.jackson.annotation.JsonProperty("requiresProfileSetup")
    private boolean requiresProfileSetup;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Chi tiết từng nguyên liệu quét được")
    public static class ScannedIngredient {

        @Schema(description = "Tên nguyên liệu tiếng Việt", example = "Cải ngọt")
        private String name;

        @Schema(description = "Phân loại chay: VEGAN (Thuần chay), LACTO_OVO (Chay trứng sữa), NON_VEG (Đồ mặn), NON_FOOD (Vật phẩm không phải thực phẩm)", example = "VEGAN")
        private String category;

        @Schema(description = "Xác nhận có phải món chay hay không", example = "true")
        @com.fasterxml.jackson.annotation.JsonProperty("isVegetarian")
        private boolean isVegetarian;

        @Schema(description = "Xác nhận có phải nguyên liệu thực phẩm ăn được hay không (false nếu là củi khô, que gỗ, đồ vật...)", example = "true")
        @com.fasterxml.jackson.annotation.JsonProperty("isEdible")
        @Builder.Default
        private boolean isEdible = true;

        @Schema(description = "Lý do cảnh báo nếu vật phẩm không ăn được hoặc không thuộc nguyên liệu nấu ăn", example = "Củi khô không phải thực phẩm nấu ăn")
        private String rejectionReason;

        @Schema(description = "Tình trạng tươi mới: FRESH (Tươi), USABLE (Cần dùng ngay), SPOILED (Đã hỏng/nấm mốc)", example = "FRESH")
        private String freshness;

        @Schema(description = "Điểm số độ tươi từ 0.0 (rất hỏng) đến 1.0 (rất tươi)", example = "0.95")
        private Double freshnessScore;

        @Schema(description = "Ước tính thời hạn sử dụng còn lại", example = "3-5 ngày")
        private String shelfLifeEstimate;

        @Schema(description = "Dấu hiệu nhận biết trực quan từ ảnh (màu sắc, vết đốm, cuống)", example = "Lá xanh mướt, thân cuống giòn, không có đốm úa")
        private String visualSigns;

        @Schema(description = "Độ tin cậy nhận diện của AI (0.0 - 1.0)", example = "0.98")
        private Double confidence;

        @Schema(description = "Mẹo bảo quản để giữ tươi lâu hơn", example = "Bọc giấy báo để trong ngăn mát tủ lạnh")
        private String storageTip;

        @Schema(description = "ID nguyên liệu đã có sẵn trong kho tủ lạnh (nếu nhận diện lại món cũ)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private java.util.UUID matchedExistingFridgeItemId;

        @Schema(description = "Có phải nguyên liệu đã được lưu vết trong tủ lạnh từ trước không", example = "true")
        @com.fasterxml.jackson.annotation.JsonProperty("isPreviouslyTracked")
        private boolean isPreviouslyTracked;

        @Schema(description = "Số ngày nguyên liệu này đã nằm trong tủ lạnh tính đến thời điểm quét", example = "3")
        private Long previousDaysInFridge;
    }
}
