package com.leis.permission.menu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.permission.menu.entity.Menu;

/**
 * 返回菜单的展示信息、权限标识和树结构。
 */
@Schema(description = "返回菜单的展示信息、权限标识和树结构")
public record MenuVO(
    @Schema(description = "记录标识")
    String id,
    @Schema(description = "上级节点标识，根节点没有上级")
    String parentId,
    @Schema(description = "显示名称")
    String name,
    @Schema(description = "用途说明")
    String description,
    @Schema(description = "节点类型，取值为 DIRECTORY、PAGE 或 BUTTON")
    String type,
    @Schema(description = "页面在前端路由表中的唯一名称")
    String routeName,
    @Schema(description = "页面的前端访问路径")
    String path,
    @Schema(description = "前端根据该键加载对应页面组件")
    String componentKey,
    @Schema(description = "后端用于授权校验的权限标识")
    String permissionCode,
    @Schema(description = "同级节点排序值，数值越小越靠前")
    Integer sortOrder,
    @Schema(description = "菜单是否在导航中可见")
    Boolean visible,
    @Schema(description = "启用状态，取值为 ENABLED 或 DISABLED")
    String status,
    @Schema(description = "用于并发校验的数据版本")
    Integer version
) {

    /**
     * 转换菜单配置、权限标识和用途说明，并以字符串传输主键。
     */
    public static MenuVO from(Menu row) {
        return new MenuVO(
                (row.getId() == null ? null : row.getId().toString()),
                (row.getParentId() == null ? null : row.getParentId().toString()),
                row.getName(),
                row.getDescription(),
                row.getType(),
                row.getRouteName(),
                row.getPath(),
                row.getComponentKey(),
                row.getPermissionCode(),
                row.getSortOrder(),
                row.getVisible(),
                row.getStatus(),
                row.getVersion()
        );
    }
}
