package com.vegeai.backend.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required.")
    @Schema(description = "Current password", example = "OldPass@123")
    private String currentPassword;

    @NotBlank(message = "New password is required.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,100}$",
        message = "New password must be between 8 and 100 characters, containing uppercase (A-Z), lowercase (a-z), number (0-9), and special characters."
    )
    @Schema(description = "New password", example = "NewPass@123456")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password.")
    @Schema(description = "Confirmation of new password", example = "NewPass@123456")
    private String confirmNewPassword;
}
