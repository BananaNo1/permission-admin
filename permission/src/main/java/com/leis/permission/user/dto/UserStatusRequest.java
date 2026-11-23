package com.leis.permission.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UserStatusRequest(
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
        @NotNull Integer version
) {
}