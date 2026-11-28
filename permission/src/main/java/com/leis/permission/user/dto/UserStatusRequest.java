package com.leis.permission.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 变更用户启用状态时提交的目标状态和修订版本。
 */
@Schema(description = "变更用户启用状态时提交的目标状态和修订版本")
public record UserStatusRequest(
    @Schema(description = "启用状态，取值为 ENABLED 或 DISABLED")
    @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
    @Schema(description = "用于并发校验的数据版本")
    @NotNull Integer version
) {
}