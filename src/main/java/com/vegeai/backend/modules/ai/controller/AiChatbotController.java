package com.vegeai.backend.modules.ai.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.ai.dto.AiChatRequest;
import com.vegeai.backend.modules.ai.dto.AiChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI Nutrition Chatbot Controller.
 * Endpoint: POST /api/ai/chat
 *
 * Rate Limit Policy:
 *  - Authenticated Users (MEMBER / ADMIN): Unlimited requests.
 *  - Guest Users (Unauthenticated): Maximum 3 trial requests per 24 hours (guarded by GuestAiChatRateLimitFilter).
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Copilot & Nutrition Chatbot", description = "AI meal recommendations and nutrition advisory endpoints.")
public class AiChatbotController {

    @PostMapping("/chat")
    @Operation(summary = "Chat with AI Nutrition Assistant", description = """
            **Trò chuyện và hỏi đáp dinh dưỡng chay với AI.**
            
            **Chính sách giới hạn (Rate Limit Policy):**
            - **Thành viên đã đăng nhập (MEMBER / ADMIN):** Không giới hạn số lượt hỏi.
            - **Khách vãng lai (Guest):** Tối đa **3 câu hỏi dùng thử mỗi 24 giờ** theo địa chỉ IP (áp dụng bộ lọc `GuestAiChatRateLimitFilter`). Sau 3 câu, hệ thống yêu cầu đăng nhập.
            
            **Tham số:**
            - `message`: Câu hỏi hoặc yêu cầu tư vấn dinh dưỡng chay (Ví dụ: "Gợi ý cho tôi bữa sáng chay giàu đạm").
            - `sessionId`: *(Tùy chọn)* Định danh phiên trò chuyện để ghi nhớ ngữ cảnh.
            """)
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isGuest = (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken);

        String username = isGuest ? "Guest" : auth.getName();
        log.info("[AI-CHAT] Processing query for user: {}. Message snippet: {}",
                username, req.getMessage().substring(0, Math.min(60, req.getMessage().length())));

        String sessionId = (req.getSessionId() != null && !req.getSessionId().isBlank())
                ? req.getSessionId()
                : UUID.randomUUID().toString();

        // Sample deterministic AI reply structure with standard vegetarian disclaimer
        AiChatResponse responseData = AiChatResponse.builder()
                .sessionId(sessionId)
                .reply("Chào bạn! Dưới đây là tư vấn dinh dưỡng chay cho bạn: Hãy đảm bảo bổ sung đủ Protein từ các nguồn đậu nành, hạt sen, nấm và vitamin B12. (Lưu ý: Lời khuyên này chỉ mang tính tham khảo dinh dưỡng, không thay thế chỉ định y khoa).")
                .recommendedRecipes(List.of("Đậu phụ sốt cà chua nấm đông cô", "Salad bơ rong biển mè rang", "Canh rong biển đậu hũ non"))
                .timestamp(Instant.now())
                .isGuestTrial(isGuest)
                .build();

        return ResponseEntity.ok(ApiResponse.success("AI response generated successfully.", responseData));
    }
}
