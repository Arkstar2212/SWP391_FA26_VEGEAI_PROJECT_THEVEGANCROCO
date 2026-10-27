package com.vegeai.backend.modules.community.controller;

import com.vegeai.backend.common.dto.*;
import com.vegeai.backend.modules.community.dto.CommunityDtos.*;
import com.vegeai.backend.modules.community.entity.VideoStatus;
import com.vegeai.backend.modules.community.service.VideoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/admin/videos") @RequiredArgsConstructor
@Tag(name = "15. Admin Videos") @SecurityRequirement(name = "bearerAuth")
public class AdminVideoController {
    private final VideoService service;
    private final com.vegeai.backend.modules.community.service.CommunityMediaAssetService assets;
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<java.util.Map<String, String>> upload(@RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue = "false") boolean video) {
        return ApiResponse.success(java.util.Map.of("url", assets.store(file, video)));
    }
    @GetMapping
    public ApiResponse<PageResponse<VideoResponse>> list(@AuthenticationPrincipal String userId,
            @RequestParam(required = false) VideoStatus status, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.adminList(userId, status, keyword, category, page, size));
    }
    @GetMapping("/{id}")
    public ApiResponse<VideoResponse> detail(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.adminDetail(userId, id));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<VideoResponse>> create(@AuthenticationPrincipal String userId, @Valid @RequestBody VideoInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(userId, input)));
    }
    @PutMapping("/{id}")
    public ApiResponse<VideoResponse> update(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody VideoInput input) {
        return ApiResponse.success(service.update(userId, id, input));
    }
    @PostMapping("/{id}/moderation")
    public ApiResponse<VideoResponse> reviewAiFlag(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @Valid @RequestBody ReviewInput input) {
        return ApiResponse.success(service.reviewAiFlag(userId, id, input));
    }
    @PostMapping("/{id}/archive")
    public ApiResponse<VideoResponse> archive(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @Valid @RequestBody Revision input) {
        return ApiResponse.success(service.archive(userId, id, input.version()));
    }
    @PostMapping("/{id}/restore")
    public ApiResponse<VideoResponse> restore(@AuthenticationPrincipal String userId, @PathVariable UUID id,
            @Valid @RequestBody Revision input) {
        return ApiResponse.success(service.restore(userId, id, input.version()));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal String userId, @PathVariable UUID id, @RequestParam long version) {
        service.delete(userId, id, version);
        return ApiResponse.success("Video deleted.", null);
    }
}
