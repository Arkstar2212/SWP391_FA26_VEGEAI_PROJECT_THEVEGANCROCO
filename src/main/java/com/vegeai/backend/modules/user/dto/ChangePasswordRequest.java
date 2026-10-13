package com.vegeai.backend.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @Schema(description = "Current password (optional if otp is provided)", example = "OldPass@123")
    private String currentPassword;

    @Schema(description = "OTP sent to email (optional if currentPassword is provided)", example = "123456")
    private String otp;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters.")
    @Schema(description = "New password", example = "NewPass@123456")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password.")
    @Schema(description = "Confirmation of new password", example = "NewPass@123456")
    private String confirmNewPassword;
}
