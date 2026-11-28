package com.leis.permission.dept.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * 修改部门时提交的属性与并发校验版本。
 */
@Schema(description = "修改部门时提交的属性与并发校验版本")
public record DeptUpdateRequest(
    @Schema(description = "上级节点标识，根节点没有上级")
    Long parentId,
    @Schema(description = "显示名称")
    @NotBlank @Size(max = 100) String name,
    @Schema(description = "同级节点排序值，数值越小越靠前")
    @NotNull @Min(0) Integer sortOrder,
    @Schema(description = "启用状态，取值为 ENABLED 或 DISABLED")
    @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
    @Schema(description = "用于并发校验的数据版本")
    @NotNull Integer version,
    @Schema(description = "省略时保留现有负责人")
    @Valid DeptManagerAssignment managerAssignment
) {
}
