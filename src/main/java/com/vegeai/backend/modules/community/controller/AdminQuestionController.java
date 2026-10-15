package com.vegeai.backend.modules.community.controller;

import com.vegeai.backend.common.dto.*;
import com.vegeai.backend.modules.community.dto.CommunityDtos.*;
import com.vegeai.backend.modules.community.entity.QuestionStatus;
import com.vegeai.backend.modules.community.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/admin/questions") @RequiredArgsConstructor
@Tag(name = "13. Admin Q&A") @SecurityRequirement(name = "bearerAuth")
public class AdminQuestionController {
    private final QuestionService service;
    @GetMapping
    public ApiResponse<PageResponse<QuestionResponse>> list(@AuthenticationPrincipal String userId,
            @RequestParam(required = false) QuestionStatus status, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.queue(userId, status, keyword, page, size));
    }
    @GetMapping("/{id}")
    public ApiResponse<QuestionResponse> detail(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.adminDetail(userId, id));
    }
    @PutMapping("/{id}/moderation") @Operation(summary = "Approve or reject a question; rejection requires a reason and current version")
    public ApiResponse<QuestionResponse> review(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody ReviewInput input) {
        return ApiResponse.success(service.review(userId, id, input));
    }
    @PutMapping("/{id}/answer") @Operation(summary = "Write or replace the admin answer; this does not automatically approve the question")
    public ApiResponse<QuestionResponse> answer(@AuthenticationPrincipal String userId, @PathVariable UUID id, @Valid @RequestBody AnswerInput input) {
        return ApiResponse.success(service.answer(userId, id, input));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal String userId, @PathVariable UUID id, @RequestParam long version) {
        service.delete(userId, id, version);
        return ApiResponse.success("Question deleted.", null);
    }
}
