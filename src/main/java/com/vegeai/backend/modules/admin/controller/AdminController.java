package com.vegeai.backend.modules.admin.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.user.dto.UserProfileResponse;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only user management.
 * All endpoints are protected by ROLE_ADMIN (enforced in SecurityConfig + @PreAuthorize).
 *
 * Admin Immunity Rule: Admins cannot modify other ADMIN accounts via API.
 * (Enforced at service layer — service to be expanded in Phase 2.)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only user and system management.")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping("/users")
    @Operation(summary = "List all users (paginated)")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> listUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<User> userPage = userRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        List<UserProfileResponse> profiles = userPage.getContent()
                .stream().map(UserProfileResponse::from).toList();

        return ResponseEntity.ok(ApiResponse.success(
                "Page " + page + " of " + userPage.getTotalPages(), profiles));
    }
}
