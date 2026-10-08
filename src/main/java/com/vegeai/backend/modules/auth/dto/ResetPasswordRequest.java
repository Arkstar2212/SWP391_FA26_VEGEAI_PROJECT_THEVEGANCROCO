package com.vegeai.backend.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email format.")
    private String email;

    @NotBlank(message = "OTP is required.")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits.")
    private String otp;

    @NotBlank(message = "New password is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "New password must be a 64-character SHA-256 hex string.")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "Confirm new password must be a 64-character SHA-256 hex string.")
    private String confirmNewPassword;
}
