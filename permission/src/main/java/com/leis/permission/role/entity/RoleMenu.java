package com.leis.permission.role.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 表示角色与菜单或操作权限之间的持久化关联。
 */
@Schema(description = "角色与菜单权限的关联")
@Getter
@Setter
@TableName("sys_role_menu")
public class RoleMenu {
    @Schema(description = "角色主键")
    private Long roleId;

    @Schema(description = "授予该角色的菜单或操作权限主键")
    private Long menuId;
}
