package com.leis.permission.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色持久化实体，保存状态、数据范围和并发校验版本。
 */
@Schema(description = "角色持久化实体")
@Getter
@Setter
@TableName("sys_role")
public class Role {
    @Schema(description = "角色主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "角色唯一编码")
    private String code;

    @Schema(description = "角色显示名称")
    private String name;

    @Schema(description = "角色状态，取值为 ENABLED 或 DISABLED")
    private String status;

    @Schema(description = "数据范围，取值为 ALL、DEPT、DEPT_AND_CHILDREN 或 SELF")
    private String dataScope;

    @Schema(description = "是否为系统预置角色")
    private Boolean builtIn;

    @Schema(description = "用于乐观锁并发校验的数据版本")
    @Version
    private Integer version;
}
