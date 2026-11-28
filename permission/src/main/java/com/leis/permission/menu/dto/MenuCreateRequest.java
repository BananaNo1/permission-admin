package com.leis.permission.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 新增目录、页面或操作权限时提交的菜单配置。
 */
@Schema(description = "新增目录、页面或操作权限时提交的菜单配置")
public record MenuCreateRequest(
    @Schema(description = "上级节点标识，根节点没有上级")
    Long parentId,
    @Schema(description = "显示名称")
    @NotBlank @Size(max = 100) String name,
    @Schema(description = "用途说明")
    @Size(max = 300) String description,
    @Schema(description = "节点类型，取值为 DIRECTORY、PAGE 或 BUTTON")
    @NotBlank @Pattern(regexp = "DIRECTORY|PAGE|BUTTON") String type,
    @Schema(description = "页面在前端路由表中的唯一名称")
    @Size(max = 100) String routeName,
    @Schema(description = "页面的前端访问路径")
    @Size(max = 200) String path,
    @Schema(description = "前端根据该键加载对应页面组件")
    @Size(max = 100) String componentKey,
    @Schema(description = "后端用于授权校验的权限标识")
    @Size(max = 100) String permissionCode,
    @Schema(description = "同级节点排序值，数值越小越靠前")
    @NotNull @Min(0) Integer sortOrder,
    @Schema(description = "菜单是否在导航中可见")
    @NotNull Boolean visible,
    @Schema(description = "启用状态，取值为 ENABLED 或 DISABLED")
    @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status
) {
}