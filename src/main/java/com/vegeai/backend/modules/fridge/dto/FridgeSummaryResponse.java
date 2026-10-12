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
@Schema(description = "Báo cáo thống kê tổng quan tình trạng thực phẩm trong tủ lạnh")
public class FridgeSummaryResponse {

    @Schema(description = "Tổng số món đang còn trong tủ lạnh (ACTIVE)", example = "12")
    private int totalActiveItems;

    @Schema(description = "Số món tươi mới an toàn (FRESH)", example = "8")
    private int freshCount;

    @Schema(description = "Số món cần dùng ngay (USABLE)", example = "3")
    private int usableCount;

    @Schema(description = "Số món sắp hết hạn trong 1-2 ngày tới", example = "2")
    private int expiringSoonCount;

    @Schema(description = "Số món đã quá hạn sử dụng", example = "1")
    private int expiredCount;

    @Schema(description = "Số lượng món thuần chay (VEGAN)", example = "10")
    private int veganCount;

    @Schema(description = "Các cảnh báo cần chú ý khẩn cấp để tránh lãng phí thực phẩm")
    private List<String> urgentWarnings;
}
