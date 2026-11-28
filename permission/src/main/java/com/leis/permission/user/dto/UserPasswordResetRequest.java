package com.leis.permission.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置目标用户密码时提交的新密码与修订版本。
 */
@Schema(description = "管理员重置目标用户密码时提交的新密码与修订版本")
public record UserPasswordResetRequest(
    @Schema(description = "更新后的账号密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank @Size(min = 6, max = 64) String newPassword,
    @Schema(description = "用于并发校验的数据版本")
    @NotNull Integer version
) {
}