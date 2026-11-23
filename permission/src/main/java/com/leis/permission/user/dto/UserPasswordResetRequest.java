package com.leis.permission.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserPasswordResetRequest(
        @NotBlank @Size(min = 6, max = 64) String newPassword,
        @NotNull Integer version
) {
}