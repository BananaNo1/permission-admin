package com.leis.permission.dept.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增部门时提交的父级、名称和其他基础属性。
 */
@Schema(description = "新增部门时提交的父级、名称和其他基础属性")
public record DeptCreateRequest(
    @Schema(description = "上级节点标识，根节点没有上级")
    Long parentId,
    @Schema(description = "显示名称")
    @NotBlank @Size(max = 100) String name,
    @Schema(description = "同级节点排序值，数值越小越靠前")
    @NotNull @Min(0) Integer sortOrder
) {
}