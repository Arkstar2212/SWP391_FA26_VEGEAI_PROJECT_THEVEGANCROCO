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
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "01. Authentication", description = "Auth endpoints: register, login, token refresh, logout, OTP, password reset.")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String REFRESH_COOKIE_PATH   = "/api/auth";

    private final AuthService authService;

    // ── 2.1 Register ─────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new account", description = """
            **Đăng ký tài khoản thành viên mới.**
            
            **Quy tắc điền thông tin:**
            - `username`: 4-50 ký tự, chỉ gồm chữ cái, số và dấu gạch dưới `_`.
            - `email`: Địa chỉ email hợp lệ để nhận mã OTP xác thực (6 số).
            - `password` & `confirmPassword`: Chuỗi mã hóa SHA-256 (64 ký tự hex) từ phía client (Ví dụ: `Admin@123` -> `e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7` hoặc `User@123` -> `aca155e1ad5b7acc67a56e1e04284b914758da42cedc8f05df939811a5b8e093`).
            - `fullName`: Họ và tên, tối đa 100 ký tự (không chứa số).
            - `phoneNumber`: *(Tùy chọn)* Đúng 10 chữ số bắt đầu bằng `0` (Ví dụ: `0912345678`) hoặc để trống `""`.
            - `dietaryPreferences`: `VEGAN`, `VEGETARIAN`, `LACTO_OVO`, `LACTO`, `OVO`, `PESCATARIAN`, `FLEXITARIAN`, `RAW_VEGAN`.
            - `allergies`: `NONE` hoặc phân tách bằng dấu phẩy: `PEANUTS`, `TREE_NUTS`, `SOY`, `GLUTEN`, `DAIRY`, `EGGS`, `SESAME`, `MUSHROOMS`, `CORN`.
            
            *Sau khi đăng ký thành công, tài khoản ở trạng thái `PENDING_VERIFICATION`. Mã OTP 6 số sẽ được gửi về email.*
            """)
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
    @Operation(summary = "Verify email with OTP", description = """
            **Kích hoạt tài khoản bằng mã OTP 6 chữ số.**
            
            - `email`: Email đã dùng để đăng ký.
            - `otp`: Mã số gồm 6 chữ số nhận từ email hoặc console backend (hạn sử dụng 3 phút).
            - Sau khi kích hoạt thành công, tài khoản chuyển sang trạng thái `ACTIVE` và có thể đăng nhập.
            """)
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        authService.verifyEmail(req);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully. You can now log in.", null));
    }

    // ── 2.2 Resend OTP ───────────────────────────────────────────────────────

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend email verification OTP", description = """
            **Gửi lại mã OTP kích hoạt tài khoản.**
            
            - `email`: Email cần gửi lại OTP.
            - Giới hạn: Thời gian chờ giữa 2 lần gửi tối thiểu là **30 giây** (cooldown).
            """)
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest req) {
        authService.resendOtp(req);
        return ResponseEntity.ok(ApiResponse.success("A new OTP has been sent to your email.", null));
    }

    // ── 2.3 Login ────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login step 1: Validate credentials & issue login 2FA OTP, or complete with OTP", description = """
            **Đăng nhập hệ thống (Hỗ trợ 2FA OTP & Remember Me).**
            
            - Bước 1: Gửi `usernameOrEmail`, `password`, `rememberMe`. Backend kiểm tra thông tin và gửi mã OTP 6 số về email.
            - Bước 2: Khi có mã OTP, gửi tiếp tới `/api/auth/verify-login-otp` hoặc truyền kèm `otp` ngay trong body này.
            """)
    public ResponseEntity<ApiResponse<?>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = extractClientIp(request);

        if (req.getOtp() != null && !req.getOtp().isBlank()) {
            VerifyLoginOtpRequest verifyReq = VerifyLoginOtpRequest.builder()
                    .usernameOrEmail(req.getUsernameOrEmail())
                    .otp(req.getOtp())
                    .rememberMe(req.getRememberMe())
                    .build();
            AuthService.LoginResult result = authService.verifyLoginOtp(verifyReq, clientIp);
            ResponseCookie refreshCookie = buildRefreshCookie(result.refreshTokenValue(), Boolean.TRUE.equals(req.getRememberMe()));
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
            return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công.", result.authResponse()));
        }

        LoginInitResponse initResponse = authService.initLogin(req, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Thông tin chính xác. Mã OTP đã được gửi.", initResponse));
    }

    @PostMapping("/verify-login-otp")
    @Operation(summary = "Verify login 2FA OTP and receive tokens", description = """
            **Xác thực mã OTP đăng nhập 6 số.**
            - `usernameOrEmail`: Tên đăng nhập hoặc email.
            - `otp`: Mã số 6 chữ số nhận từ email.
            - `rememberMe`: true (giữ đăng nhập 1 ngày) hoặc false (phiên làm việc tạm thời).
            """)
    public ResponseEntity<ApiResponse<AuthResponse>> verifyLoginOtp(
            @Valid @RequestBody VerifyLoginOtpRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = extractClientIp(request);
        AuthService.LoginResult result = authService.verifyLoginOtp(req, clientIp);

        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshTokenValue(), Boolean.TRUE.equals(req.getRememberMe()));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công.", result.authResponse()));
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate with Google OAuth2 ID Token", description = """
            **Đăng nhập bằng tài khoản Google (OAuth 2.0 / Google Identity Services).**
            - `idToken`: Chuỗi JWT ID Token do Google cấp sau khi người dùng xác thực thành công.
            - `rememberMe`: true (lưu phiên 1 ngày) hoặc false.
            - Tự động tạo tài khoản mới (Role: MEMBER, Trạng thái: ACTIVE) nếu lần đầu đăng nhập.
            """)
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = extractClientIp(request);
        AuthService.LoginResult result = authService.loginWithGoogle(req, clientIp);

        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshTokenValue(), Boolean.TRUE.equals(req.getRememberMe()));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.success("Đăng nhập Google thành công.", result.authResponse()));
    }

    @PostMapping("/resend-login-otp")
    @Operation(summary = "Resend login 2FA OTP", description = "Gửi lại mã OTP đăng nhập (thời gian chờ tối thiểu 30 giây).")
    public ResponseEntity<ApiResponse<Void>> resendLoginOtp(
            @Valid @RequestBody ResendLoginOtpRequest req) {
        authService.resendLoginOtp(req.getUsernameOrEmail());
        return ResponseEntity.ok(ApiResponse.success("Mã OTP đăng nhập mới đã được gửi đến email của bạn.", null));
    }


    // ── 2.4 Refresh ──────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Get a new access token using HttpOnly refresh cookie or X-Refresh-Token header", description = """
            **Làm mới Access Token khi phiên đăng nhập hết hạn.**
            
            - Trình duyệt tự động gửi kèm HttpOnly cookie `refreshToken`.
            - Khi test bằng Postman/cURL không có cookie, có thể gửi qua header `X-Refresh-Token`.
            - Trả về Access Token mới và xoay vòng (rotate) Refresh Token mới.
            """)
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = request.getHeader("X-Refresh-Token");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new com.vegeai.backend.common.exception.AppException(
                    ErrorCode.REFRESH_TOKEN_NOT_FOUND,
                    "Refresh token not found. Please provide via HttpOnly cookie or X-Refresh-Token header.");
        }
        AuthService.LoginResult result = authService.refresh(refreshToken.trim());

        // Refresh Token Rotation (RTR): Update client's HttpOnly cookie with the newly rotated token
        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshTokenValue());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.success("Token refreshed.", result.authResponse()));
    }

    // ── 2.5 Logout ───────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Logout — blacklist access token and revoke refresh token", description = """
            **Đăng xuất an toàn khỏi hệ thống.**
            
            - Thu hồi Refresh Token trong cơ sở dữ liệu.
            - Đưa Access Token hiện tại vào Blacklist (Redis/Memory) để vô hiệu hóa ngay lập tức.
            - Xóa sạch cookie `refreshToken` trên trình duyệt.
            """)
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Extract Bearer token (may be null if already expired/not present)
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String bearerToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;

        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = request.getHeader("X-Refresh-Token");
        }
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
    @Operation(summary = "Request password reset OTP", description = """
            **Yêu cầu đặt lại mật khẩu qua email.**
            
            - `email`: Địa chỉ email tài khoản của bạn.
            - Cơ chế bảo mật chống dò quét tài khoản (Anti-enumeration): Hệ thống luôn trả về thông báo chung thành công dù email có tồn tại hay không.
            - Nếu email có trong hệ thống, mã OTP 6 số sẽ được gửi về hòm thư (và log console).
            """)
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        // Always same response to prevent account enumeration
        return ResponseEntity.ok(ApiResponse.success(
                "If the email exists, an OTP has been sent.", null));
    }

    // ── 2.6 Reset Password ────────────────────────────────────────────────────

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using OTP received by email", description = """
            **Thiết lập mật khẩu mới bằng mã OTP.**
            
            - `email`: Địa chỉ email cần đổi mật khẩu.
            - `otp`: Mã xác nhận 6 chữ số nhận từ email hoặc console backend.
            - `newPassword` & `confirmNewPassword`: Chuỗi SHA-256 hex (64 ký tự) của mật khẩu mới.
            """)
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. Please log in with your new password.", null));
    }



    // ── Cookie helpers ────────────────────────────────────────────────────────

    private ResponseCookie buildRefreshCookie(String tokenValue, boolean rememberMe) {
        var builder = ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokenValue)
                .httpOnly(true)
                .secure(false)     // Set to true in production (HTTPS)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH);

        if (rememberMe) {
            builder.maxAge(Duration.ofDays(1)); // exactly 1 day (24 hours) as requested
        } else {
            builder.maxAge(-1); // browser session cookie (cleared when browser closes)
        }
        return builder.build();
    }

    private ResponseCookie buildRefreshCookie(String tokenValue) {
        return buildRefreshCookie(tokenValue, true);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
