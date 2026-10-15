package com.vegeai.backend.modules.community.controller;

import com.vegeai.backend.common.dto.*;
import com.vegeai.backend.modules.community.dto.BlogDtos.*;
import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.service.BlogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/blog") @RequiredArgsConstructor
@Tag(name = "16. Community Blog")
public class BlogController {
    private final BlogService service;

    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostResponse>> published(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) BlogCategory category, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        return ApiResponse.success(service.published(keyword, category, page, size));
    }
    @GetMapping("/posts/{id}")
    public ApiResponse<PostResponse> published(@PathVariable UUID id) { return ApiResponse.success(service.published(id)); }

    @GetMapping("/my-posts")
    public ApiResponse<PageResponse<PostResponse>> mine(@AuthenticationPrincipal String userId,
            @RequestParam(required = false) BlogStatus status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        return ApiResponse.success(service.mine(userId, status, page, size));
    }
    @GetMapping("/my-posts/{id}")
    public ApiResponse<PostResponse> own(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.own(userId, id));
    }
    @PostMapping("/my-posts")
    public ResponseEntity<ApiResponse<PostResponse>> create(@AuthenticationPrincipal String userId, @Valid @RequestBody PostInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(userId, input)));
    }
    @PutMapping("/my-posts/{id}")
    public ApiResponse<PostResponse> update(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody PostInput input) {
        return ApiResponse.success(service.update(userId, id, input));
    }
    @PostMapping("/my-posts/{id}/submit")
    public ApiResponse<PostResponse> submit(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody Revision input) {
        return ApiResponse.success(service.submit(userId, id, input.version()));
    }
    @PostMapping("/my-posts/{id}/withdraw")
    public ApiResponse<PostResponse> withdraw(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody Revision input) {
        return ApiResponse.success(service.withdraw(userId, id, input.version()));
    }
    @DeleteMapping("/my-posts/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal String userId, @PathVariable UUID id, @RequestParam(required = false) Long version) {
        service.delete(userId, id, version);
        return ApiResponse.success("Blog post deleted.", null);
    }
}
