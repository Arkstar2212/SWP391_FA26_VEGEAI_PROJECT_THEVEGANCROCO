package com.vegeai.backend.modules.community.controller;

import com.vegeai.backend.common.dto.*;
import com.vegeai.backend.modules.community.dto.BlogDtos.*;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.service.BlogService;
import com.vegeai.backend.modules.community.dto.BlogDtos.Revision;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/admin/blog/posts") @RequiredArgsConstructor
@Tag(name = "17. Admin Blog") @SecurityRequirement(name = "bearerAuth")
public class AdminBlogController {
    private final BlogService service;

    @GetMapping
    public ApiResponse<PageResponse<PostResponse>> queue(@AuthenticationPrincipal String userId,
            @RequestParam(required = false) BlogStatus status, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.queue(userId, status, keyword, page, size));
    }
    @GetMapping("/{id}")
    public ApiResponse<PostResponse> detail(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.reviewPost(userId, id));
    }
    @PostMapping("/{id}/review")
    public ApiResponse<PostResponse> review(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody ReviewInput input) {
        return ApiResponse.success(service.review(userId, id, input));
    }
    @PutMapping("/{id}")
    public ApiResponse<PostResponse> update(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @Valid @RequestBody PostInput input) {
        return ApiResponse.success(service.adminUpdate(userId, id, input));
    }
    @PostMapping("/{id}/archive")
    public ApiResponse<PostResponse> archive(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @Valid @RequestBody Revision input) {
        return ApiResponse.success("Blog post moved to the archive.", service.adminArchive(userId, id, input.version()));
    }
    @PostMapping("/{id}/restore")
    public ApiResponse<PostResponse> restore(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @Valid @RequestBody Revision input) {
        return ApiResponse.success("Blog post restored.", service.adminRestore(userId, id, input.version()));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @RequestParam Long version) {
        service.adminDelete(userId, id, version);
        return ApiResponse.success("Blog post permanently deleted.", null);
    }
}
