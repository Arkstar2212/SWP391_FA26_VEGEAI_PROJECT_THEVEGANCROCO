package com.vegeai.backend.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to update the authenticated user's username")
public class ChangeUsernameRequest {

    @NotBlank(message = "Username is required.")
    @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters.")
    @Pattern(
            regexp = "^(?=.*[a-zA-Z])[a-zA-Z0-9_]{4,50}$",
            message = "Username must be 4-50 characters, contain at least one letter, and only use letters, digits, or underscores."
    )
    @Schema(description = "New unique username (4-50 characters, letters, numbers, underscores)", example = "vege_lover")
    private String username;
}
