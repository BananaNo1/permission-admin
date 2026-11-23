package com.leis.permission.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_user_role")
public class UserRole {
    private Long userId;
    private Long roleId;
}
