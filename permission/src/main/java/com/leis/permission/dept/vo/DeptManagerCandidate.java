package com.leis.permission.dept.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 供部门负责人选择器展示的账号。 */
@Schema(description = "部门负责人候选账号")
public record DeptManagerCandidate(
        @Schema(description = "用户标识") String id,
        @Schema(description = "显示名称") String nickname,
        @Schema(description = "登录账号") String username
) {}
