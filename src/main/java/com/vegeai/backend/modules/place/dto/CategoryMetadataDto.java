package com.vegeai.backend.modules.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin danh mục địa điểm ăn uống và mua sắm chay")
public class CategoryMetadataDto {

    @Schema(description = "Mã danh mục", example = "RESTAURANT")
    private PlaceCategory category;

    @Schema(description = "Tên hiển thị tiếng Việt", example = "Quán ăn & Nhà hàng chay")
    private String displayName;

    @Schema(description = "Mô tả chi tiết mục đích", example = "Phục vụ khi bạn muốn đi ăn ngoài, họp mặt gia đình hoặc tìm buffet chay")
    private String description;

    @Schema(description = "Biểu tượng gợi ý cho giao diện (Icon code)", example = "utensils")
    private String icon;
}
