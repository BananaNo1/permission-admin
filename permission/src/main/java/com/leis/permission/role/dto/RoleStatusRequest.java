package com.leis.permission.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 提交角色目标状态及用于并发校验的修订版本。
 */
@Schema(description = "提交角色目标状态及用于并发校验的修订版本")
public record RoleStatusRequest(
    @Schema(description = "启用状态，取值为 ENABLED 或 DISABLED")
    @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
    @Schema(description = "用于并发校验的数据版本")
    @NotNull Integer version
) {
}
