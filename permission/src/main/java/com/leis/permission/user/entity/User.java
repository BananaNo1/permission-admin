package com.leis.permission.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户账号持久化实体，包含组织归属、密码摘要及授权版本。
 */
@Schema(description = "用户账号持久化实体")
@Getter
@Setter
@TableName("sys_user")
public class User {
    @Schema(description = "用户主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "用于认证的密码摘要", hidden = true)
    private String passwordHash;

    @Schema(description = "用户显示名称")
    private String nickname;

    @Schema(description = "所属部门主键")
    private Long deptId;

    @Schema(description = "账号状态，取值为 ENABLED 或 DISABLED")
    private String status;

    @Schema(description = "是否为受系统保护的账号")
    private Boolean protectedAccount;

    @Schema(description = "用于校验现有登录会话是否有效的授权版本")
    private Long authVersion;

    @Schema(description = "用于乐观锁并发校验的数据版本")
    @Version
    private Integer version;
}