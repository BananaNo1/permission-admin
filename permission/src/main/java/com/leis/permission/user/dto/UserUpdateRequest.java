package com.leis.permission.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改用户基础资料时提交的字段与修订版本。
 */
@Schema(description = "修改用户基础资料时提交的字段与修订版本")
public record UserUpdateRequest(
    @Schema(description = "用户显示名称")
    @NotBlank @Size(max = 64) String nickname,
    @Schema(description = "所属部门标识")
    @NotNull Long deptId,
    @Schema(description = "用于并发校验的数据版本")
    @NotNull Integer version
) {
}
