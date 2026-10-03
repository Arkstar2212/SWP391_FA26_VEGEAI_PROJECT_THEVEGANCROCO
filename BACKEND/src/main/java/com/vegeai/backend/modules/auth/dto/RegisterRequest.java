package com.vegeai.backend.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Registration request DTO.
 * VEGEAI-specific: No CCCD required. Healthcare fields (height, weight, dietary, allergies)
 * are optional and part of profile, not mandatory at registration.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required.")
    @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters.")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers and underscores.")
    private String username;

    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email format.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String password;

    @NotBlank(message = "Password confirmation is required.")
    private String confirmPassword;

    @NotBlank(message = "Full name is required.")
    @Pattern(regexp = "^[^0-9]+$", message = "Full name must not contain digits.")
    @Size(max = 100, message = "Full name must not exceed 100 characters.")
    private String fullName;

    // ── Optional fields (can be updated later in profile) ─────────────────
    @Pattern(regexp = "^0\\d{9}$", message = "Phone number must be a valid Vietnamese number (10 digits starting with 0).")
    private String phoneNumber;

    /** VEGAN, VEGETARIAN, LACTO_OVO */
    private String dietaryPreferences;

    /** Comma-separated allergens */
    private String allergies;
}
