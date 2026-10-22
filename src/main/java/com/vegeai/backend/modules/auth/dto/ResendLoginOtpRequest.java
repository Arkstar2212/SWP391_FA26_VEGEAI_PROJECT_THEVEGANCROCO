package com.vegeai.backend.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendLoginOtpRequest {

    @NotBlank(message = "Username or email is required.")
    @Schema(description = "Registered username or email", example = "member@vegeai.com")
    private String usernameOrEmail;
}
