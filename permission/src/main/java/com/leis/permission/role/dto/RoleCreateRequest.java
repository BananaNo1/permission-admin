package com.leis.permission.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "ALL|DEPT|DEPT_AND_CHILDREN|SELF") String dataScope
) {
}