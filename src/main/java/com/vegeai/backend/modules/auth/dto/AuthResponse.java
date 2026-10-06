package com.vegeai.backend.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Response payload returned after successful login.
 * Access token is in the JSON body; refresh token is in an HttpOnly cookie.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private final String accessToken;
    private final String tokenType;
    private final long expiresInSeconds;

    // ── User info (safe fields only — no passwordHash) ───────────────────────
    private final UUID userId;
    private final String username;
    private final String email;
    private final String fullName;
    private final String role;
    private final String status;
}
