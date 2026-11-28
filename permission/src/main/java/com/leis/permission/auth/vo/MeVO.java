package com.leis.permission.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/**
 * 返回当前账号的基础信息及前端权限判断所需的授权字段。
 */
@Schema(description = "返回当前账号的基础信息及前端权限判断所需的授权字段")
public record MeVO (
    @Schema(description = "记录标识")
    String id,
    @Schema(description = "登录账号")
    String username,
    @Schema(description = "所属部门标识")
    String deptId,
    @Schema(description = "是否为超级管理员")
    boolean admin,
    @Schema(description = "当前账号可用的权限标识集合")
    Set<String> permissions
) {
}
