package com.vegeai.backend.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Login request: accepts username OR email + SHA-256 pre-hashed password.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Username or email is required.")
    @Schema(description = "Tên đăng nhập hoặc email (Ví dụ: admin@vegeai.com hoặc member@vegeai.com)", example = "admin@vegeai.com")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "Password must be a 64-character SHA-256 hex string.")
    @Schema(description = "Mật khẩu đã băm SHA-256 phía client (Mặc định là hash của Admin@123)", example = "e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7")
    private String password;

    @Schema(description = "Remember me flag (keeps session refreshed for 1 day / 24 hours)", example = "true")
    private Boolean rememberMe;

    @Schema(description = "Optional 6-digit login verification OTP", example = "482731")
    private String otp;
}
