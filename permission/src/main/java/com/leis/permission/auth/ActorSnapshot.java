package com.leis.permission.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/**
 * 当前请求的身份与授权快照，包含角色权限及组织数据范围。
 */
@Schema(description = "当前请求身份与授权快照")
public record ActorSnapshot(
        @Schema(description = "当前用户主键")
        Long id,
        @Schema(description = "当前用户登录账号")
        String username,
        @Schema(description = "当前用户所属部门主键")
        Long deptId,
        @Schema(description = "是否具有超级管理员角色")
        boolean admin,
        @Schema(description = "当前用户可用的操作权限标识")
        Set<String> permissions,
        @Schema(description = "是否拥有全部用户数据范围")
        boolean all,
        @Schema(description = "部门数据授权覆盖的部门主键集合")
        Set<Long> deptIds,
        @Schema(description = "是否包含本人用户记录")
        boolean includeSelf
) {
}