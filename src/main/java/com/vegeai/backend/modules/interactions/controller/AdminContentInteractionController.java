package com.vegeai.backend.modules.interactions.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.interactions.dto.InteractionDtos.AdminRating;
import com.vegeai.backend.modules.interactions.dto.InteractionDtos.CommentView;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import com.vegeai.backend.modules.interactions.service.ContentInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Administrative inspection and removal controls for member comments and ratings. */
@RestController
@RequestMapping("/api/admin/content-interactions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentInteractionController {
    private final ContentInteractionService service;

    @GetMapping("/comments")
    public ApiResponse<PageResponse<CommentView>> comments(@RequestParam(required=false) InteractionTarget type,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.adminComments(type,page,size));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@AuthenticationPrincipal String userId,@PathVariable UUID id,
            @RequestParam Long version) {
        service.adminDeleteComment(userId,id,version);
        return ApiResponse.success("Comment removed.",null);
    }

    @GetMapping("/ratings")
    public ApiResponse<PageResponse<AdminRating>> ratings(@RequestParam(required=false) InteractionTarget type,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.adminRatings(type,page,size));
    }

    @DeleteMapping("/ratings/{id}")
    public ApiResponse<Void> deleteRating(@AuthenticationPrincipal String userId,@PathVariable UUID id) {
        service.adminDeleteRating(userId,id);
        return ApiResponse.success("Rating removed.",null);
    }
}
