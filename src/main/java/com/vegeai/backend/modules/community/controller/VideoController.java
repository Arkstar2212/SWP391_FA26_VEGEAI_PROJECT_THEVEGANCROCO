package com.vegeai.backend.modules.community.controller;

import com.vegeai.backend.common.dto.*;
import com.vegeai.backend.modules.community.dto.CommunityDtos.*;
import com.vegeai.backend.modules.community.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/videos") @RequiredArgsConstructor
@Tag(name = "14. Community Videos", description = "Database-backed public videos, member comments and private favorites.")
public class VideoController {
    private final VideoService service;
    private final com.vegeai.backend.modules.community.service.CommunityMediaAssetService assets;
    @GetMapping("/mine")
    public ApiResponse<PageResponse<VideoResponse>> mine(@AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(service.mine(userId, page, size));
    }
    @GetMapping("/mine/{id}")
    public ApiResponse<VideoResponse> mineDetail(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.mineDetail(userId, id));
    }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<java.util.Map<String, String>> upload(@RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue = "false") boolean video) {
        return ApiResponse.success(java.util.Map.of("url", assets.store(file, video)));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<VideoResponse>> create(@AuthenticationPrincipal String userId, @Valid @RequestBody VideoInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createMember(userId, input)));
    }
    @PutMapping("/mine/{id}")
    public ApiResponse<VideoResponse> updateMine(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody VideoInput input) {
        return ApiResponse.success(service.updateMember(userId, id, input));
    }
    @GetMapping @Operation(summary = "Search published videos by keyword/category with pagination")
    public ApiResponse<PageResponse<VideoResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) { return ApiResponse.success(service.published(keyword, category, page, size)); }
    @GetMapping("/{id}")
    public ApiResponse<VideoResponse> detail(@PathVariable UUID id) { return ApiResponse.success(service.published(id)); }
    @GetMapping("/{id}/comments")
    public ApiResponse<PageResponse<CommentResponse>> comments(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return ApiResponse.success(service.comments(id, page, size)); }
    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> comment(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody CommentInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.comment(userId, id, input)));
    }
    @PutMapping("/{id}/comments/{commentId}")
    public ApiResponse<CommentResponse> editComment(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @PathVariable UUID commentId, @Valid @RequestBody CommentInput input) {
        return ApiResponse.success(service.editComment(userId, id, commentId, input));
    }
    @DeleteMapping("/{id}/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @PathVariable UUID commentId, @RequestParam long version) {
        service.deleteComment(userId, id, commentId, version);
        return ApiResponse.success("Comment deleted.", null);
    }
    @GetMapping("/favorites") @Operation(summary = "List only my saved published videos")
    public ApiResponse<PageResponse<VideoResponse>> favorites(@AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(service.favorites(userId, page, size));
    }
    @GetMapping("/{id}/favorite")
    public ApiResponse<FavoriteResponse> favorite(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.favorite(userId, id));
    }
    @PutMapping("/{id}/favorite") @Operation(summary = "Save video; repeated requests do not create duplicates")
    public ApiResponse<FavoriteResponse> saveFavorite(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.saveFavorite(userId, id));
    }
    @DeleteMapping("/{id}/favorite")
    public ApiResponse<FavoriteResponse> removeFavorite(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.removeFavorite(userId, id));
    }
}
