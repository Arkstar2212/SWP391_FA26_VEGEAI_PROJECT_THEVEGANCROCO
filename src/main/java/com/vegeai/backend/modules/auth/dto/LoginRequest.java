package com.vegeai.backend.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login request: accepts username OR email + plain-text password.
 * Note: Unlike the auth-service spec's SHA-256 pre-hashing, VEGEAI
 * relies on HTTPS and server-side BCrypt hashing for security simplicity.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Username or email is required.")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required.")
    private String password;
}
