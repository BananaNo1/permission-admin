package com.leis.permission.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增角色时提交的角色编码、名称和数据范围等配置。
 */
@Schema(description = "新增角色时提交的角色编码、名称和数据范围等配置")
public record RoleCreateRequest(
    @Schema(description = "角色编码以大写字母开头，仅允许大写字母、数字和下划线，长度为 2–64 位", example = "TEST_REVIEWER")
    @NotBlank(message = "请填写角色编码")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "角色编码须以大写字母开头，仅支持大写字母、数字和下划线，长度为 2–64 位") String code,
    @Schema(description = "显示名称")
    @NotBlank(message = "请填写角色名称") @Size(max = 100, message = "角色名称最多 100 个字符") String name,
    @Schema(description = "数据范围，支持 ALL、DEPT、DEPT_AND_CHILDREN 和 SELF")
    @NotBlank(message = "请选择数据范围")
    @Pattern(regexp = "ALL|DEPT|DEPT_AND_CHILDREN|SELF", message = "数据范围须为 ALL、DEPT、DEPT_AND_CHILDREN 或 SELF") String dataScope
) {
}