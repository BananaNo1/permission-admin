package com.leis.permission.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoleStatusRequest(
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
        @NotNull Integer version
) {
}
