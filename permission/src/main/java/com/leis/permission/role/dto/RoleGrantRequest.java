package com.leis.permission.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record RoleGrantRequest(
        @NotNull Set<@NotNull Long> menuIds,
        @NotNull Integer version
) {
}
