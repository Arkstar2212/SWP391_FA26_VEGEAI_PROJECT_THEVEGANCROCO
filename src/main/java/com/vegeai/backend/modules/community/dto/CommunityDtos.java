package com.vegeai.backend.modules.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vegeai.backend.modules.community.entity.QuestionStatus;
import com.vegeai.backend.modules.community.entity.VideoStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

public final class CommunityDtos {
    private CommunityDtos() {}
    public enum Decision { APPROVE, REJECT }
    public record QuestionInput(@NotBlank @Size(min = 5, max = 5000) String content) {}
    public record ReviewInput(@NotNull Decision decision, @Size(max = 1000) String reason,
                              @NotNull @PositiveOrZero Long version) {}
    public record Revision(@NotNull @PositiveOrZero Long version) {}
    public record AnswerInput(@NotBlank @Size(max = 10000) String content, @NotNull @PositiveOrZero Long version) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionResponse(UUID id, String authorName, String content, QuestionStatus status,
                                   String answer, String answeredBy, LocalDateTime answeredAt,
                                   String moderationReason, String reviewedBy, LocalDateTime reviewedAt,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, long version) {}
    public record VideoInput(@NotBlank @Size(max = 200) String title,
                             @Size(max = 10000) String description,
                             @NotBlank @Size(max = 2000) String videoUrl,
                             @Size(max = 2000) String thumbnailUrl,
                             @Size(max = 80) String category,
                             @Positive @Max(86400) Integer durationSeconds,
                             @NotNull VideoStatus status, @PositiveOrZero Long version) {}
    public record VideoResponse(UUID id, String title, String description, String videoUrl,
                                String thumbnailUrl, String category, Integer durationSeconds,
                                VideoStatus status, com.vegeai.backend.modules.community.entity.AiModerationStatus aiModerationStatus,
                                String aiModerationReason, Boolean aiModerationAgreedWithAdmin,
                                String createdBy, LocalDateTime createdAt,
                                LocalDateTime updatedAt, long version) {}
    public record CommentInput(@NotBlank @Size(max = 2000) String content, @PositiveOrZero Long version) {}
    public record CommentResponse(UUID id, UUID videoId, UUID authorId, String authorName, String content,
                                  LocalDateTime createdAt, LocalDateTime updatedAt, long version) {}
    public record FavoriteResponse(UUID videoId, boolean favorite) {}
}
