package com.vegeai.backend.modules.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vegeai.backend.modules.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Safe user profile response — never exposes passwordHash or sensitive internal fields.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private final UUID userId;
    private final String username;
    private final String email;
    private final String fullName;
    private final String phoneNumber;
    private final LocalDate dateOfBirth;
    private final String gender;
    private final String address;
    private final String role;
    private final String status;
    private final String dietaryPreferences;
    private final String allergies;
    private final Double heightCm;
    private final Double weightKg;
    private final Double bmi;
    private final Integer dailyCalorieTarget;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastLogin;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .address(user.getAddress())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .dietaryPreferences(user.getDietaryPreferences())
                .allergies(user.getAllergies())
                .heightCm(user.getHeightCm())
                .weightKg(user.getWeightKg())
                .bmi(user.getBmi())
                .dailyCalorieTarget(user.getDailyCalorieTarget())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }
}
