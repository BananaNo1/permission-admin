package com.leis.permission.menu.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单与操作权限的持久化实体，包含层级、路由和权限标识。
 */
@Schema(description = "菜单及操作权限持久化实体")
@Getter
@Setter
@TableName("sys_menu")
public class Menu {
    @Schema(description = "菜单或操作权限主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "上级菜单主键，顶级菜单为空")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    @Schema(description = "菜单或操作权限显示名称")
    private String name;

    @Schema(description = "菜单或操作权限的用途说明")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    @Schema(description = "节点类型，取值为 DIRECTORY、PAGE 或 BUTTON")
    private String type;

    @Schema(description = "前端路由名称")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String routeName;

    @Schema(description = "前端路由路径")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String path;

    @Schema(description = "前端注册的页面组件标识")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String componentKey;

    @Schema(description = "用于鉴权的权限标识")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String permissionCode;

    @Schema(description = "同级节点排序值，数值越小越靠前")
    private Integer sortOrder;

    @Schema(description = "是否在前端菜单中显示")
    private Boolean visible;

    @Schema(description = "节点状态，取值为 ENABLED 或 DISABLED")
    private String status;

    @Schema(description = "用于乐观锁并发校验的数据版本")
    @Version
    private Integer version;
}