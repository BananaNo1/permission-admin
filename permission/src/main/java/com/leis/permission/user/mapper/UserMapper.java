package com.leis.permission.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 提供用户账号持久化访问和写操作使用的锁定查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    /**
     * 按用户 ID 锁定行，供权限受控的账号变更事务使用。
     */
    User lock(@Param("id") Long id);
}
