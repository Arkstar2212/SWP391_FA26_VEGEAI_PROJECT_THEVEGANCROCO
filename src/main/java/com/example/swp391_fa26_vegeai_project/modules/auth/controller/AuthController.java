package com.example.swp391_fa26_vegeai_project.modules.auth.controller;

import com.example.swp391_fa26_vegeai_project.common.dto.ApiResponse;
import com.example.swp391_fa26_vegeai_project.modules.auth.dto.AuthResponse;
import com.example.swp391_fa26_vegeai_project.modules.auth.dto.LoginRequest;
import com.example.swp391_fa26_vegeai_project.modules.auth.dto.RegisterRequest;
import com.example.swp391_fa26_vegeai_project.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }
}
