package com.leis.permission.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 提交角色可编辑属性及用于并发校验的修订版本。
 */
@Schema(description = "提交角色可编辑属性及用于并发校验的修订版本")
public record RoleUpdateRequest(
    @Schema(description = "显示名称")
    @NotBlank @Size(max = 100) String name,
    @Schema(description = "数据范围，支持 ALL、DEPT、DEPT_AND_CHILDREN 和 SELF")
    @NotBlank @Pattern(regexp = "ALL|DEPT|DEPT_AND_CHILDREN|SELF") String dataScope,
    @Schema(description = "用于并发校验的数据版本")
    @NotNull Integer version
) {

}
