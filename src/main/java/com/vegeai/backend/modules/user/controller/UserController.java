package com.vegeai.backend.modules.user.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.user.dto.ChangePasswordRequest;
import com.vegeai.backend.modules.user.dto.PublicUserProfileResponse;
import com.vegeai.backend.modules.user.dto.UpdateUserProfileRequest;
import com.vegeai.backend.modules.user.dto.UserProfileResponse;
import com.vegeai.backend.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User profile & account management endpoints.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile, dietary settings, credentials, and account lifecycle management.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user's profile", description = "Fetches the full profile, dietary preferences, and BMI of the authenticated user.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal String userId) {
        UserProfileResponse profile = userService.getMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user's profile", description = "Updates personal info, dietary preferences, and recalculates deterministic BMI if height/weight change.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdateUserProfileRequest req) {
        UserProfileResponse updated = userService.updateMyProfile(userId, req);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully.", updated));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change password", description = "Validates existing password, updates with new password, and invalidates all active refresh tokens.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(userId, req);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully. Please log in with your new password on other devices.", null));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Deactivate current account", description = "Soft-deletes/disables the current user account and invalidates all active sessions.")
    public ResponseEntity<ApiResponse<Void>> deactivateMyAccount(
            @AuthenticationPrincipal String userId) {
        userService.deactivateMyAccount(userId);
        return ResponseEntity.ok(ApiResponse.success("Your account has been deactivated successfully.", null));
    }

    @GetMapping("/{userId}/public")
    @Operation(summary = "Get public user profile", description = "Public profile information safe for community feeds, comments, and recipes.")
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> getPublicProfile(
            @PathVariable UUID userId) {
        PublicUserProfileResponse publicProfile = userService.getPublicProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(publicProfile));
    }
}
