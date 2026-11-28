package com.leis.permission.dept.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 部门持久化实体，记录组织层级、状态、排序和修订信息。
 */
@Schema(description = "部门持久化实体")
@Getter
@Setter
@TableName("sys_dept")
public class Dept {
    @Schema(description = "部门主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "上级部门主键，根部门为空")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "部门负责人用户主键，未配置时为空")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long managerUserId;

    @Schema(description = "同级部门排序值，数值越小越靠前")
    private Integer sortOrder;

    @Schema(description = "部门状态，取值为 ENABLED 或 DISABLED")
    private String status;

    @Schema(description = "用于乐观锁并发校验的数据版本")
    @Version
    private Integer version;
}