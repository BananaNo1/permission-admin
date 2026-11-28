package com.leis.permission.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * 提交角色的菜单权限选择及用于并发校验的修订版本。
 */
@Schema(description = "提交角色的菜单权限选择及用于并发校验的修订版本")
public record RoleGrantRequest(
    @Schema(description = "角色可访问的菜单和操作权限标识集合")
    @NotNull Set<@NotNull Long> menuIds,
    @Schema(description = "用于并发校验的数据版本")
    @NotNull Integer version
) {
}
