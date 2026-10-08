package com.vegeai.backend.modules.admin.dto;

import com.vegeai.backend.modules.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRoleRequest {

    @NotNull(message = "User role is required.")
    @Schema(description = "New role (MEMBER or ADMIN)", example = "ADMIN")
    private UserRole role;
}
