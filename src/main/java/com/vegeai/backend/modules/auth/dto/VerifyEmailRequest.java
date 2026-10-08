package com.vegeai.backend.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyEmailRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email format.")
    @Schema(description = "Insert registered email address", example = "user@email.com")
    private String email;

    @NotBlank(message = "OTP is required.")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits.")
    @Schema(description = "Insert 6-digit OTP received via email", example = "123456")
    private String otp;
}
