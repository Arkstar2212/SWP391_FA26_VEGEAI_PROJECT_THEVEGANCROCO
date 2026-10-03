package com.vegeai.backend.modules.auth.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.auth.dto.*;
import com.vegeai.backend.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * Authentication controller.
 *
 * Endpoints:
 *  POST /api/auth/register          — create account (PENDING_VERIFICATION)
 *  POST /api/auth/verify-email      — confirm OTP → ACTIVE
 *  POST /api/auth/resend-otp        — resend activation OTP (30s cooldown)
 *  POST /api/auth/login             — login → access token + HttpOnly refresh cookie
 *  POST /api/auth/refresh           — new access token via refresh cookie
 *  POST /api/auth/logout            — dual invalidation
 *  POST /api/auth/forgot-password   — send reset OTP (anti-enumeration)
 *  POST /api/auth/reset-password    — confirm OTP + new password
 *  GET  /api/auth/error-codes       — list all error codes (dev/debug)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Auth endpoints: register, login, token refresh, logout, OTP, password reset.")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String REFRESH_COOKIE_PATH   = "/api/auth";

    private final AuthService authService;

    // ── 2.1 Register ─────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new account", description = "Creates account with PENDING_VERIFICATION status and sends OTP to email.")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>builder()
                        .success(true)
                        .code(201)
                        .message("Account created successfully. Please check your email for the verification OTP.")
                        .timestamp(java.time.Instant.now().toString())
                        .build());
    }

    // ── 2.2 Verify Email ─────────────────────────────────────────────────────

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with OTP")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        authService.verifyEmail(req);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully. You can now log in.", null));
    }

    // ── 2.2 Resend OTP ───────────────────────────────────────────────────────

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend email verification OTP", description = "Rate-limited: 30 seconds cooldown between requests.")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest req) {
        authService.resendOtp(req);
        return ResponseEntity.ok(ApiResponse.success("A new OTP has been sent to your email.", null));
    }

    // ── 2.3 Login ────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login and receive access token + HttpOnly refresh cookie")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletResponse response) {

        AuthService.LoginResult result = authService.login(req);

        // Set refresh token as HttpOnly cookie
        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshTokenValue());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.success("Login successful.", result.authResponse()));
    }

    // ── 2.4 Refresh ──────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Get a new access token using the HttpOnly refresh cookie")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null) {
            ApiResponse<AuthResponse> errorResponse = ApiResponse.<AuthResponse>builder()
                    .success(false)
                    .code(ErrorCode.REFRESH_TOKEN_NOT_FOUND.getCode())
                    .message("Refresh token cookie not present.")
                    .timestamp(java.time.Instant.now().toString())
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
        AuthResponse authResponse = authService.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed.", authResponse));
    }

    // ── 2.5 Logout ───────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Logout — blacklist access token and revoke refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Extract Bearer token (may be null if already expired/not present)
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String bearerToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;

        String refreshToken = extractRefreshCookie(request);
        authService.logout(bearerToken, refreshToken);

        // Clear the cookie in client
        ResponseCookie clearCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(false) // set true in production with HTTPS
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully.", null));
    }

    // ── 2.6 Forgot Password ───────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset OTP", description = "Anti-enumeration: always returns the same message regardless of whether email exists.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        // Always same response to prevent account enumeration
        return ResponseEntity.ok(ApiResponse.success(
                "If the email exists, an OTP has been sent.", null));
    }

    // ── 2.6 Reset Password ────────────────────────────────────────────────────

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using OTP received by email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. Please log in with your new password.", null));
    }

    // ── Error codes reference (dev/debug) ─────────────────────────────────────

    @GetMapping("/error-codes")
    @Operation(summary = "List all error codes", description = "Developer reference for all application error codes and their HTTP status.")
    public ResponseEntity<ApiResponse<Map<String, Object>[]>> errorCodes() {
        @SuppressWarnings("unchecked")
        Map<String, Object>[] codes = Arrays.stream(ErrorCode.values())
                .map(ec -> Map.of(
                        "name", ec.name(),
                        "code", ec.getCode(),
                        "message", ec.getDefaultMessage(),
                        "httpStatus", ec.getHttpStatus().value()
                ))
                .toArray(Map[]::new);
        return ResponseEntity.ok(ApiResponse.success(codes));
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    private ResponseCookie buildRefreshCookie(String tokenValue) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokenValue)
                .httpOnly(true)
                .secure(false)     // Set to true in production (HTTPS)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(1))
                .build();
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
