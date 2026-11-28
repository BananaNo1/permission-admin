package com.leis.permission.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.role.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 提供角色表访问和需要行锁的角色查询。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    /**
     * 按角色 ID 锁定数据库行，供角色维护和授权事务使用。
     */
    Role lock(@Param("id") Long id);
}
