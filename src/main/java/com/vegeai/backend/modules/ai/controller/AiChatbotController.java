package com.vegeai.backend.modules.ai.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.ai.dto.AiChatRequest;
import com.vegeai.backend.modules.ai.dto.AiChatResponse;
import com.vegeai.backend.modules.ai.service.GeminiAiService;
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

    private final GeminiAiService geminiAiService;

    @PostMapping("/chat")
    @Operation(summary = "Chat with AI Nutrition Assistant", description = """
            **Trò chuyện và hỏi đáp dinh dưỡng chay với AI (Tích hợp Google Gemini).**
            
            **Chính sách giới hạn (Rate Limit Policy):**
            - **Thành viên đã đăng nhập (MEMBER / ADMIN):** Không giới hạn số lượt hỏi. Tự động cá nhân hóa theo hồ sơ sức khỏe (BMI, dị ứng, mục tiêu calo).
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

        AiChatResponse responseData = geminiAiService.processChat(req, username, isGuest);

        return ResponseEntity.ok(ApiResponse.success("AI response generated successfully.", responseData));
    }
}
