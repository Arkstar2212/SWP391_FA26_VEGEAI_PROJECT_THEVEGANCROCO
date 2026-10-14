package com.vegeai.backend.modules.ai.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.ai.dto.FridgeScanResponse;
import com.vegeai.backend.modules.ai.service.GeminiAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI Computer Vision & Smart Fridge Scanner Controller.
 * Endpoint: POST /api/ai/fridge-scan
 *
 * Implements FEAT-05 (FR-VIS-01 -> FR-VIS-05) from PRD:
 *  - Multimodal Vision Ingredient Recognition.
 *  - Vegan / Vegetarian vs Non-Vegetarian Detection & Warning.
 *  - Visual Freshness Level Estimation (Fresh / Usable / Spoiled).
 *  - Personalized Allergy Cross-Checking with User Profile.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "06. AI Vision & Fridge Scanner", description = "Endpoints nhận diện nguyên liệu, thẩm định đồ chay và độ tươi từ ảnh chụp tủ lạnh.")
public class AiVisionController {

    private final GeminiAiService geminiAiService;

    @PostMapping(value = "/fridge-scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Quét ảnh tủ lạnh: Nhận diện nguyên liệu, xác định đồ chay & đánh giá độ tươi",
            description = """
                    **Tính năng Quét tủ lạnh & Giám định độ tươi thực phẩm (Tích hợp Google Gemini Multimodal Vision):**
                    
                    **Quy trình xử lý:**
                    1. **Nhận diện nguyên liệu (Ingredient Recognition):** Trích xuất tên các loại rau, củ, quả, nấm, đậu, gia vị... trong ảnh.
                    2. **Phân loại Chay / Mặn (Vegetarian Verification):**
                       - `VEGAN`: Thuần chay (rau củ, nấm, đậu...).
                       - `LACTO_OVO`: Chay có trứng, sữa.
                       - `NON_VEG`: Thịt, cá, hải sản, xúc xích mặn... $\\rightarrow$ Cảnh báo đỏ (`hasNonVegetarianItem = true`).
                    3. **Đánh giá độ tươi trực quan (Visual Freshness Estimation):**
                       - `FRESH`: Tươi ngon (dùng trong 3-5 ngày).
                       - `USABLE`: Héo nhẹ, cần dùng sớm trong 24-48 giờ.
                       - `SPOILED`: Dập nát, thâm đen, có mốc $\\rightarrow$ Cảnh báo không được ăn.
                    4. **Cá nhân hóa dị ứng:** Nếu người dùng đã đăng nhập, tự động cảnh báo nguyên liệu trùng với hồ sơ dị ứng cá nhân.
                    5. **Gợi ý món chay (Cook Now):** Đề xuất 2-3 món chay ngon nấu từ các nguyên liệu còn tươi trong ảnh.
                    
                    **Yêu cầu ảnh chụp:**
                    - Định dạng: JPG, JPEG, PNG, WEBP.
                    - Dung lượng tối đa: **5MB**.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Phân tích ảnh thành công",
                    content = @Content(schema = @Schema(implementation = FridgeScanResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "File ảnh rỗng hoặc không đúng định dạng"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413",
                    description = "Dung lượng ảnh vượt quá 5MB"
            )
    })
    public ResponseEntity<ApiResponse<FridgeScanResponse>> scanFridge(
            @Parameter(description = "File ảnh chụp tủ lạnh hoặc khay nguyên liệu (tối đa 5MB, JPG/PNG/WEBP)", required = true)
            @RequestParam("image") MultipartFile image
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isGuest = (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken);
        String username = isGuest ? "Guest" : auth.getName();

        log.info("[AI-VISION] Receiving fridge scan request from user '{}' (Filename: '{}', Size: {} bytes, Type: '{}')",
                username, image.getOriginalFilename(), image.getSize(), image.getContentType());

        FridgeScanResponse scanResult = geminiAiService.scanFridge(image, username, isGuest);

        return ResponseEntity.ok(ApiResponse.success("Phân tích ảnh tủ lạnh thành công.", scanResult));
    }
}
