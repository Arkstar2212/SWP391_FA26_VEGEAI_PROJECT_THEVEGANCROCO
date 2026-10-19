package com.vegeai.backend.modules.interactions.dto;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
/** Request and response payloads for blog and recipe comments/ratings. */
public final class InteractionDtos{
 private InteractionDtos(){}
 public record RatingInput(@Min(1) @Max(5) int stars){}
 public record CommentInput(@NotBlank @Size(max=2000) String content,@PositiveOrZero Long version){}
 public record RatingSummary(InteractionTarget targetType,UUID targetId,Double averageStars,long ratingCount,Integer myRating){}
 public record CommentView(UUID id,InteractionTarget targetType,UUID targetId,UUID authorId,String authorName,String content,LocalDateTime createdAt,LocalDateTime updatedAt,long version){}
 public record ContentView(RatingSummary rating,PageResponse<CommentView> comments){}
 public record AdminRating(UUID id,InteractionTarget targetType,UUID targetId,UUID userId,String userName,int stars,LocalDateTime updatedAt){}
}
