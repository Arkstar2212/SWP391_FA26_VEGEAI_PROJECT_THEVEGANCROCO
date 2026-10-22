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
                            @Size(max = 1000) String videoUrl,
                            @PositiveOrZero Long version) {}
    public record Revision(@NotNull @PositiveOrZero Long version) {}
    public record ReviewInput(@NotNull @PositiveOrZero Long version, @NotNull Decision decision,
                              @Size(max = 2000) String reason) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostResponse(UUID postId, UUID authorId, String authorName, String title, String excerpt,
                               String content, String coverImage, String videoUrl, Double ratingAverage, Integer ratingCount,
                               BlogCategory category, BlogStatus status,
                               long version, int readingMinutes, LocalDateTime createdAt, LocalDateTime updatedAt,
                               LocalDateTime submittedAt, LocalDateTime reviewedAt, LocalDateTime publishedAt,
                               String reviewerName, String moderationReason,
                               String aiStatus, String aiReason) {}

    public record BlogCommentInput(@NotBlank @Size(max = 2000) String content,
                                  @Min(1) @Max(5) Integer rating) {}

    public record BlogCommentResponse(UUID id, UUID postId, UUID authorId, String authorName,
                                      String content, Integer rating, LocalDateTime createdAt) {}
}
