package com.vegeai.backend.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyLoginOtpRequest {

    @NotBlank(message = "Username or email is required.")
    @Schema(description = "Registered username or email", example = "member@vegeai.com")
    private String usernameOrEmail;

    @NotBlank(message = "OTP code is required.")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits.")
    @Schema(description = "6-digit OTP code received via email", example = "482731")
    private String otp;

    @Schema(description = "Remember me flag (keeps session refreshed for 1 day / 24 hours)", example = "true")
    private Boolean rememberMe;
}
