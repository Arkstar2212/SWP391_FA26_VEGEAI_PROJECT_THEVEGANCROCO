package com.vegeai.backend.modules.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả AI phân tích kiến thức, lối sống xanh và gợi ý cá nhân hóa từ bài đăng")
public class BlogInsightAiResponse {

    @Schema(description = "ID bài viết đã phân tích")
    private UUID postId;

    @Schema(description = "Tiêu đề đúc kết giá trị cốt lõi của bài viết")
    private String headline;

    @Schema(description = "Nhãn chuyên mục của bài viết")
    private String categoryBadge;

    @Schema(description = "Danh sách 3-5 luận điểm cốt lõi AI tổng hợp từ nội dung bài viết")
    private List<String> keyTakeaways;

    @Schema(description = "Phân tích góc nhìn dinh dưỡng thực vật / lối sống xanh & lưu ý kiểm chứng thực tế")
    private String scientificOrLifestyleInsight;

    @Schema(description = "Các gợi ý hành động thực tế gắn với bài viết và tính năng của VEGEAI")
    private List<String> actionableSteps;

    @Schema(description = "Ghi chú cá nhân hóa theo chế độ ăn chay, dị ứng và mục tiêu sức khỏe của thành viên")
    private String personalizedNote;

    @Schema(description = "Khuyến cáo an toàn dinh dưỡng / miễn trừ trách nhiệm y khoa")
    private String nutritionSafetyDisclaimer;

    @Schema(description = "Câu hỏi gợi ý để tiếp tục hỏi sâu hơn trên Chatbot AI Bếp Trưởng")
    private String suggestedFollowUpPrompt;

    @Schema(description = "Nguồn tạo phân tích (AI)")
    private String source;

    @Schema(description = "Tên model AI đã thực hiện phân tích")
    private String model;

    @Schema(description = "Thời điểm tạo kết quả phân tích")
    private Instant generatedAt;
}
