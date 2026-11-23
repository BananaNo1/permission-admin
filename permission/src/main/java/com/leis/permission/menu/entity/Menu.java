package com.leis.permission.menu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_menu")
public class Menu {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String type;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String routeName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String path;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String componentKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String permissionCode;
    private Integer sortOrder;
    private Boolean visible;
    private String status;
    @Version
    private Integer version;
}