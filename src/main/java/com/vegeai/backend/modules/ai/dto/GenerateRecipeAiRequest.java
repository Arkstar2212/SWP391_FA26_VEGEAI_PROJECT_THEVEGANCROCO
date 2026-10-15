package com.vegeai.backend.modules.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tạo công thức món ăn chi tiết từ AI (Cook Now / Generate Recipe)")
public class GenerateRecipeAiRequest {

    @NotBlank(message = "Tên món ăn không được để trống.")
    @Size(max = 200, message = "Tên món ăn không quá 200 ký tự.")
    @Schema(description = "Tên món ăn muốn chế biến", example = "Canh cải ngọt nấu nấm bào ngư thanh đạm")
    private String recipeName;

    @Schema(description = "Danh sách nguyên liệu hiện có sẵn", example = "[\"Cải ngọt\", \"Nấm bào ngư\", \"Đậu phụ non\"]")
    private List<String> availableIngredients;

    @Schema(description = "Định danh phiên hội thoại AI (nếu tạo từ AI chat)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String sessionId;

    @Size(max = 500, message = "Ghi chú ngữ cảnh tối đa 500 ký tự.")
    @Schema(description = "Ghi chú tùy biến thêm từ người dùng", example = "Nấu ít dầu mỡ, không dùng ngũ vị tân")
    private String contextNotes;

    @Schema(description = "Tự động lưu vào sổ tay cá nhân của người dùng (mặc định: true)", example = "true")
    private Boolean autoSave;
}
