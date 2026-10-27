package com.vegeai.backend.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeEmailRequest {

    @NotBlank(message = "New email is required.")
    @Email(message = "New email is invalid.")
    private String newEmail;

    @NotBlank(message = "Current password is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "Current password must be a 64-character SHA-256 hex string.")
    private String currentPassword;
}
