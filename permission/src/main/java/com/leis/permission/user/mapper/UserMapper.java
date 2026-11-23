package com.leis.permission.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    /**
     * 按用户 ID 锁定行，供权限受控的账号变更事务使用。
     */
    @Select("select * from sys_user where id = #{id} for update")
    User lock(Long id);
}
