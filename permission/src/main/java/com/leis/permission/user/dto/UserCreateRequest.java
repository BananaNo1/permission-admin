package com.leis.permission.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增用户时提交的账号、初始密码和部门等信息。
 */
@Schema(description = "新增用户时提交的账号、初始密码和部门等信息")
public record UserCreateRequest(
        @Schema(description = "登录账号")
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,31}") String username,
        @Schema(description = "新建账号的初始密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 6, max = 64) String password,
        @Schema(description = "用户显示名称")
        @NotBlank @Size(max = 64) String nickname,
        @Schema(description = "所属部门标识")
        @NotNull Long deptId
) {
}