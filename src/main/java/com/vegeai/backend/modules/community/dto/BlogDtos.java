package com.vegeai.backend.modules.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vegeai.backend.modules.community.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

public final class BlogDtos {
    private BlogDtos() {}
    public enum Decision { APPROVE, REJECT }
    public record PostInput(@NotBlank @Size(max = 150) String title,
                            @Size(max = 400) String excerpt, @Size(max = 50000) String content,
                            @NotNull BlogCategory category, @Size(max = 1000) String coverImage,
                            @PositiveOrZero Long version) {}
    public record Revision(@NotNull @PositiveOrZero Long version) {}
    public record ReviewInput(@NotNull @PositiveOrZero Long version, @NotNull Decision decision,
                              @Size(max = 2000) String reason) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostResponse(UUID postId, UUID authorId, String authorName, String title, String excerpt,
                               String content, String coverImage, BlogCategory category, BlogStatus status,
                               long version, int readingMinutes, LocalDateTime createdAt, LocalDateTime updatedAt,
                               LocalDateTime submittedAt, LocalDateTime reviewedAt, LocalDateTime publishedAt,
                               AiModerationStatus aiModerationStatus, String aiModerationReason,
                               Boolean aiModerationAgreedWithAdmin,
                               String reviewerName, String moderationReason) {}
}
