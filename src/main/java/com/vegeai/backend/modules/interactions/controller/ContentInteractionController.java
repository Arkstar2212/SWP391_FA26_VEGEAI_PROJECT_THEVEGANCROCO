package com.vegeai.backend.modules.interactions.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.interactions.dto.InteractionDtos.*;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import com.vegeai.backend.modules.interactions.service.ContentInteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import java.util.List;

/** Public reads and member-only writes for ratings and comments on community content. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContentInteractionController {
    private final ContentInteractionService service;

    @GetMapping("/public/content-interactions/{type}/{id}")
    public ApiResponse<ContentView> publicContent(@PathVariable InteractionTarget type, @PathVariable UUID id,
            @AuthenticationPrincipal String userId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.publicContent(type,id,userId,page,size));
    }

    @GetMapping("/public/content-interactions/{type}/ratings")
    public ApiResponse<List<RatingSummary>> publicRatings(@PathVariable InteractionTarget type,
            @RequestParam List<UUID> ids) {
        return ApiResponse.success(service.publicRatingSummaries(type, ids));
    }

    @PutMapping("/content-interactions/{type}/{id}/rating")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<RatingSummary> rate(@AuthenticationPrincipal String userId, @PathVariable InteractionTarget type,
            @PathVariable UUID id, @Valid @RequestBody RatingInput input) {
        return ApiResponse.success(service.rate(userId,type,id,input.stars()));
    }

    @DeleteMapping("/content-interactions/{type}/{id}/rating")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<RatingSummary> removeRating(@AuthenticationPrincipal String userId,
            @PathVariable InteractionTarget type,@PathVariable UUID id) {
        return ApiResponse.success(service.removeRating(userId,type,id));
    }

    @PostMapping("/content-interactions/{type}/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<CommentView> addComment(@AuthenticationPrincipal String userId,
            @PathVariable InteractionTarget type,@PathVariable UUID id,@Valid @RequestBody CommentInput input) {
        return ApiResponse.created("Comment created.",service.addComment(userId,type,id,input));
    }

    @PostMapping(value="/content-interactions/{type}/{id}/reviews", consumes="multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<CommentView> addReview(@AuthenticationPrincipal String userId,
            @PathVariable InteractionTarget type,@PathVariable UUID id,
            @Valid @RequestPart("review") ReviewInput input,
            @RequestPart(value="photos", required=false) List<MultipartFile> photos) {
        return ApiResponse.created("Review created.",service.addReview(userId,type,id,input,photos));
    }

    @PutMapping(value="/content-interactions/{type}/{id}/comments/{commentId}/review", consumes="multipart/form-data")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<CommentView> editReview(@AuthenticationPrincipal String userId,
            @PathVariable InteractionTarget type,@PathVariable UUID id,@PathVariable UUID commentId,
            @Valid @RequestPart("review") ReviewInput input,
            @RequestPart(value="photos", required=false) List<MultipartFile> photos) {
        return ApiResponse.success(service.editReview(userId,type,id,commentId,input,photos));
    }

    @PutMapping("/content-interactions/{type}/{id}/comments/{commentId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<CommentView> editComment(@AuthenticationPrincipal String userId,
            @PathVariable InteractionTarget type,@PathVariable UUID id,@PathVariable UUID commentId,
            @Valid @RequestBody CommentInput input) {
        return ApiResponse.success(service.editComment(userId,type,id,commentId,input));
    }

    @DeleteMapping("/content-interactions/{type}/{id}/comments/{commentId}")
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN')")
    public ApiResponse<Void> deleteComment(@AuthenticationPrincipal String userId,
            @PathVariable InteractionTarget type,@PathVariable UUID id,@PathVariable UUID commentId,
            @RequestParam Long version) {
        service.deleteComment(userId,type,id,commentId,version);
        return ApiResponse.success("Comment deleted.",null);
    }
}
