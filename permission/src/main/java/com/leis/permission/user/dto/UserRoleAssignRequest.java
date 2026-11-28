package com.leis.permission.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * 分配用户角色时提交的角色标识集合与修订版本。
 */
@Schema(description = "分配用户角色时提交的角色标识集合与修订版本")
public record UserRoleAssignRequest(
    @Schema(description = "为用户分配的角色标识集合")
    @NotNull Set<@NotNull Long> roleIds,
    @Schema(description = "用于并发校验的数据版本")
    @NotNull Integer version
) {
}
