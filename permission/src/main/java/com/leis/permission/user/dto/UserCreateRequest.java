package com.leis.permission.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,31}") String username,
        @NotBlank @Size(min = 12, max = 64) String password,
        @NotBlank @Size(max = 64) String nickname,
        @NotNull Long deptId
) {
}