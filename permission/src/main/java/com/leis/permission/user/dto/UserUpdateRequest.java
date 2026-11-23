package com.leis.permission.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank @Size(max = 64) String nickname,
        @NotNull Long deptId,
        @NotNull Integer version
) {
}
