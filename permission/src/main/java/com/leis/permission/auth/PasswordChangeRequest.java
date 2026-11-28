package com.leis.permission.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录用户修改个人密码的请求参数。
 */
@Schema(description = "登录用户修改个人密码的请求参数")
public record PasswordChangeRequest(
    @Schema(description = "用于校验身份的当前密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank String oldPassword,
    @Schema(description = "更新后的账号密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank @Size(min = 6, max = 64) String newPassword
) {
}