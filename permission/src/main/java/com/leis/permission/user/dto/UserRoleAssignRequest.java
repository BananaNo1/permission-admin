package com.leis.permission.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UserRoleAssignRequest(
        @NotNull Set<@NotNull Long> roleIds,
        @NotNull Integer version
) {
}
