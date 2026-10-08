package com.vegeai.backend.modules.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vegeai.backend.modules.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public-facing profile response — safe to display on community recipes, blogs, comments.
 * Strictly excludes PII (email, phone, address, private health details, status).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicUserProfileResponse {

    private final UUID userId;
    private final String username;
    private final String fullName;
    private final String dietaryPreferences;
    private final String role;
    private final LocalDateTime createdAt;

    public static PublicUserProfileResponse from(User user) {
        return PublicUserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .dietaryPreferences(user.getDietaryPreferences())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
