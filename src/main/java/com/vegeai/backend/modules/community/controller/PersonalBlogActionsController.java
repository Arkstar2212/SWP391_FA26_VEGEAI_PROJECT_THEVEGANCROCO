package com.vegeai.backend.modules.community.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.community.dto.BlogDtos.PostInput;
import com.vegeai.backend.modules.community.dto.BlogDtos.PostResponse;
import com.vegeai.backend.modules.community.service.PersonalBlogActionsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** New personal blog actions; existing blog routes keep their previous behavior. */
@RestController
@RequestMapping("/api/blog/my-posts")
@RequiredArgsConstructor
public class PersonalBlogActionsController {
    private final PersonalBlogActionsService service;

    @PutMapping("/{id}/request-review")
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN')")
    public ApiResponse<PostResponse> requestPublishedEdit(@AuthenticationPrincipal String userId,
            @PathVariable UUID id, @Valid @RequestBody PostInput input) {
        return ApiResponse.success("Changes submitted for review.", service.requestPublishedEdit(userId, id, input));
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN')")
    public ApiResponse<Void> deleteOwnedPost(@AuthenticationPrincipal String userId,
            @PathVariable UUID id, @RequestParam Long version) {
        service.deleteOwnedPost(userId, id, version);
        return ApiResponse.success("Blog post deleted.", null);
    }
}
