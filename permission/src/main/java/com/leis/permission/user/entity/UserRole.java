package com.leis.permission.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 表示用户与角色之间的持久化关联。
 */
@Schema(description = "用户与角色的关联")
@Getter
@Setter
@TableName("sys_user_role")
public class UserRole {
    @Schema(description = "用户主键")
    private Long userId;

    @Schema(description = "授予该用户的角色主键")
    private Long roleId;
}
