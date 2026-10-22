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
@Schema(description = "Request payload for Google OAuth2 ID Token login")
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID Token is required")
    @Schema(description = "JWT ID token issued by Google Identity Services", example = "eyJhbGciOiJSUzI1NiIs...")
    private String idToken;

    @Schema(description = "Keep session active for 1 day", example = "true")
    private Boolean rememberMe;
}
