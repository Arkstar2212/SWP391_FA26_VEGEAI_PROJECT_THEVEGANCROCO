package com.vegeai.backend.modules.community.controller;

import com.vegeai.backend.common.dto.*;
import com.vegeai.backend.modules.community.dto.CommunityDtos.*;
import com.vegeai.backend.modules.community.entity.QuestionStatus;
import com.vegeai.backend.modules.community.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/questions") @RequiredArgsConstructor
@Tag(name = "12. Community Q&A", description = "Public approved questions and authenticated question submissions.")
public class QuestionController {
    private final QuestionService service;

    @GetMapping @Operation(summary = "Search approved questions; page starts at 0, size is 1..50")
    public ApiResponse<PageResponse<QuestionResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean answered, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(service.published(keyword, answered, page, size));
    }

    @GetMapping("/{id}") @Operation(summary = "Read an approved question and its admin answer")
    public ApiResponse<QuestionResponse> detail(@PathVariable UUID id) { return ApiResponse.success(service.published(id)); }

    @PostMapping @Operation(summary = "Submit a question for admin review; identity and status are assigned by the server")
    public ResponseEntity<ApiResponse<QuestionResponse>> create(@AuthenticationPrincipal String userId, @Valid @RequestBody QuestionInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(userId, input)));
    }

    @GetMapping("/mine") @Operation(summary = "Read my questions, including pending and rejected submissions")
    public ApiResponse<PageResponse<QuestionResponse>> mine(@AuthenticationPrincipal String userId,
            @RequestParam(required = false) QuestionStatus status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) { return ApiResponse.success(service.mine(userId, status, page, size)); }

    @GetMapping("/mine/{id}")
    public ApiResponse<QuestionResponse> mineDetail(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.mine(userId, id));
    }
}
