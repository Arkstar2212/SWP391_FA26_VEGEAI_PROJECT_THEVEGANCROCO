package com.vegeai.backend.modules.interactions.dto;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
/** Request and response payloads for blog and recipe comments/ratings. */
public final class InteractionDtos{
 private InteractionDtos(){}
 public record RatingInput(@Min(1) @Max(5) int stars){}
 public record CommentInput(@NotBlank @Size(max=2000) String content,@PositiveOrZero Long version){}
 public record RatingSummary(InteractionTarget targetType,UUID targetId,Double averageStars,long ratingCount,Integer myRating){}
 public record CommentView(UUID id,InteractionTarget targetType,UUID targetId,UUID authorId,String authorName,String content,Integer reviewStars,List<String> tags,List<String> photoUrls,boolean publicDisplayName,LocalDateTime createdAt,LocalDateTime updatedAt,long version){}
 public record ReviewInput(@NotBlank @Size(max=500) String content,@Min(1) @Max(5) int stars,
   @Size(max=5) List<@NotBlank @Size(max=40) String> tags,boolean publicDisplayName,
   @Size(max=4) List<@NotBlank @Size(max=255) String> photoUrls,@PositiveOrZero Long version){}
 public record ContentView(RatingSummary rating,PageResponse<CommentView> comments){}
 public record AdminRating(UUID id,InteractionTarget targetType,UUID targetId,UUID userId,String userName,int stars,LocalDateTime updatedAt){}
}
