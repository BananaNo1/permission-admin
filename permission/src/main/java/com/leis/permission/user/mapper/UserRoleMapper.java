package com.leis.permission.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.user.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供用户角色关联的持久化操作。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
