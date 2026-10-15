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
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal String userId, @PathVariable UUID id, @RequestParam long version) {
        service.delete(userId, id, version);
        return ApiResponse.success("Video deleted.", null);
    }
}
