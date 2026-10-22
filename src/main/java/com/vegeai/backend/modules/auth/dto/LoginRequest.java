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
    @Schema(description = "Insert registered username or email", example = "user@email.com")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "Password must be a 64-character SHA-256 hex string.")
    @Schema(description = "Enter password - client-side 64-character SHA-256 pre-hashed hex string", example = "aca155e1ad5b7acc67a56e1e04284b914758da42cedc8f05df939811a5b8e093")
    private String password;

    @Schema(description = "Remember me flag (keeps session refreshed for 1 day / 24 hours)", example = "true")
    private Boolean rememberMe;

    @Schema(description = "Optional 6-digit login verification OTP", example = "123456")
    private String otp;
}
