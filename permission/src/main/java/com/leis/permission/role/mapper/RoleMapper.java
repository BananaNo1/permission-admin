package com.leis.permission.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.role.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    /**
     * 按角色 ID 锁定数据库行，供角色维护和授权事务使用。
     */
    @Select("select * from sys_role where id = #{id} for update")
    Role lock(Long id);
}
