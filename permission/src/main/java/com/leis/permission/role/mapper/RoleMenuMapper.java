package com.leis.permission.role.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.role.entity.RoleMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供角色与菜单授权关系的持久化操作。
 */
@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenu> {
}
