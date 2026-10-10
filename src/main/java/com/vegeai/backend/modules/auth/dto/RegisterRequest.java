package com.vegeai.backend.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "Insert username (4-50 characters, letters, numbers, underscores)", example = "insert_username_text")
    private String username;

    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email format.")
    @Schema(description = "Insert valid email address to receive OTP", example = "user@email.com")
    private String email;

    @NotBlank(message = "Password is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "Password must be a 64-character SHA-256 hex string.")
    @Schema(description = "Enter password - client-side 64-character SHA-256 pre-hashed hex string", example = "aca155e1ad5b7acc67a56e1e04284b914758da42cedc8f05df939811a5b8e093")
    private String password;

    @NotBlank(message = "Password confirmation is required.")
    @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "Confirm password must be a 64-character SHA-256 hex string.")
    @Schema(description = "Confirm password - matching 64-character SHA-256 pre-hashed hex string", example = "aca155e1ad5b7acc67a56e1e04284b914758da42cedc8f05df939811a5b8e093")
    private String confirmPassword;

    @NotBlank(message = "Full name is required.")
    @Pattern(regexp = "^[^0-9]+$", message = "Full name must not contain digits.")
    @Size(max = 100, message = "Full name must not exceed 100 characters.")
    @Schema(description = "Insert full name text", example = "Nguyen Van A")
    private String fullName;

    // ── Optional fields (can be updated later in profile) ─────────────────
    @Pattern(regexp = "^$|^0\\d{9}$", message = "Phone number must be a valid Vietnamese number (10 digits starting with 0).")
    @Schema(description = "Insert 10-digit phone number starting with 0 (optional)", example = "0977498038")
    private String phoneNumber;

    /** VEGAN, VEGETARIAN, LACTO_OVO, etc. */
    @Schema(
        description = "Dietary preference tag. Options: VEGAN (Thuần chay 100%), VEGETARIAN (Ăn chay chung), LACTO_OVO (Chay có trứng & sữa), LACTO (Chay có sữa), OVO (Chay có trứng), PESCATARIAN (Chay kèm hải sản), FLEXITARIAN (Bán chay), RAW_VEGAN (Thuần chay thô)",
        example = "VEGAN",
        allowableValues = {"VEGAN", "VEGETARIAN", "LACTO_OVO", "LACTO", "OVO", "PESCATARIAN", "FLEXITARIAN", "RAW_VEGAN"}
    )
    private String dietaryPreferences;

    /** Comma-separated allergens */
    @Schema(
        description = "Known food allergies comma-separated. Common options: NONE, PEANUTS (Đậu phộng), TREE_NUTS (Hạt óc chó/hạnh nhân/điều), SOY (Đậu nành/đậu phụ), GLUTEN (Lúa mì), DAIRY (Sữa động vật), EGGS (Trứng), SESAME (Mè/Vừng), MUSHROOMS (Nấm), CORN (Bắp/Ngô)",
        example = "NONE"
    )
    private String allergies;
}
