package com.vegeai.backend.modules.admin.dto;

import com.vegeai.backend.modules.user.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class UpdateUserStatusRequest {

    @NotNull(message = "Account status is required.")
    @Schema(description = "New account status (ACTIVE, LOCKED, DISABLED, PENDING_VERIFICATION)", example = "LOCKED")
    private AccountStatus status;

    @Size(max = 255, message = "Reason cannot exceed 255 characters.")
    @Schema(description = "Optional reason for status change (audit trail)", example = "Violation of community standards.")
    private String reason;
}
