package com.leis.permission.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "ALL|DEPT|DEPT_AND_CHILDREN|SELF") String dataScope,
        @NotNull Integer version
) {

}
