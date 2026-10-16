package com.vegeai.backend.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Change-password payload where every field is pre-hashed by the client (SHA-256 hex),
 * matching the login / register / reset-password contract.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordHashedRequest {

    @NotBlank(message = "Current password is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "Current password must be a 64-character SHA-256 hex string.")
    @Schema(description = "Current password - client-side 64-character SHA-256 pre-hashed hex string", example = "aca155e1ad5b7acc67a56e1e04284b914758da42cedc8f05df939811a5b8e093")
    private String currentPassword;

    @NotBlank(message = "New password is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "New password must be a 64-character SHA-256 hex string.")
    @Schema(description = "New password - client-side 64-character SHA-256 pre-hashed hex string", example = "aca155e1ad5b7acc67a56e1e04284b914758da42cedc8f05df939811a5b8e093")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "Confirm new password must be a 64-character SHA-256 hex string.")
    @Schema(description = "Confirmation of new password - matching 64-character SHA-256 pre-hashed hex string", example = "aca155e1ad5b7acc67a56e1e04284b914758da42cedc8f05df939811a5b8e093")
    private String confirmNewPassword;
}
