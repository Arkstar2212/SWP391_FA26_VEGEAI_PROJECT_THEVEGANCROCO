package com.example.swp391_fa26_vegeai_project.modules.ai.controller;

import com.example.swp391_fa26_vegeai_project.common.dto.ApiResponse;
import com.example.swp391_fa26_vegeai_project.modules.ai.dto.AiChatRequest;
import com.example.swp391_fa26_vegeai_project.modules.ai.dto.AiChatResponse;
import com.example.swp391_fa26_vegeai_project.modules.ai.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        AiChatResponse response = aiChatService.processChat(request);
        return ResponseEntity.ok(ApiResponse.ok("AI chat response generated", response));
    }
}
