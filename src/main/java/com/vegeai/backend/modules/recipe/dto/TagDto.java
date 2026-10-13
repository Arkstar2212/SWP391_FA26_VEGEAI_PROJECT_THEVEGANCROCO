package com.vegeai.backend.modules.recipe.dto;

import com.vegeai.backend.modules.recipe.entity.TagGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin thẻ tag phân loại món ăn")
public class TagDto {

    @Schema(description = "ID thẻ tag", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID tagId;

    @Schema(description = "Mã định danh duy nhất (viết hoa)", example = "HIGH_PROTEIN")
    private String code;

    @Schema(description = "Tên hiển thị tiếng Việt", example = "Giàu đạm (High-Protein)")
    private String name;

    @Schema(description = "Nhóm thẻ (DIETARY_TYPE, COOKING_METHOD, DISH_CATEGORY, NUTRITION_GOAL, OCCASION, DIETARY_RESTRICTION)", example = "NUTRITION_GOAL")
    private TagGroup group;

    @Schema(description = "Mô tả ý nghĩa thẻ", example = "Món ăn cung cấp từ 15g đạm thực vật trở lên mỗi khẩu phần")
    private String description;
}
