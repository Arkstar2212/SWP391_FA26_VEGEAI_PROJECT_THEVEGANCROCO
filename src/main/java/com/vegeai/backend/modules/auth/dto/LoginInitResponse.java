package com.vegeai.backend.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginInitResponse {

    @Schema(description = "Always true when 2FA OTP is required for login", example = "true")
    private boolean requireOtp;

    @Schema(description = "Masked email where OTP was sent", example = "m***r@vegeai.com")
    private String maskedEmail;

    @Schema(description = "User identifier used for login", example = "member")
    private String usernameOrEmail;

    @Schema(description = "Remember me flag status", example = "true")
    private boolean rememberMe;

    @Schema(description = "Instruction message", example = "Mã xác thực đăng nhập đã được gửi tới email của bạn.")
    private String message;
}
