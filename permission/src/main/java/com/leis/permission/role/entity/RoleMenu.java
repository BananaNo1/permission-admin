package com.leis.permission.role.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_role_menu")
public class RoleMenu {
    private Long roleId;
    private Long menuId;
}
