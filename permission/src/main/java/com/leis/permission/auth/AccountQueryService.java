package com.leis.permission.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.leis.permission.role.entity.Role;
import com.leis.permission.role.mapper.RoleMapper;
import com.leis.permission.user.entity.User;
import com.leis.permission.user.entity.UserRole;
import com.leis.permission.user.mapper.UserMapper;
import com.leis.permission.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountQueryService {
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    /**
     * 按账号精确查找用户，供登录认证使用。
     */
    public User findByUsername(String username) {
        return userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
    }

    /**
     * 按主键读取账号，用于校验会话对应的用户状态。
     */
    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 查询用户关联的角色，仅保留当前处于启用状态的角色。
     */
    public List<Role> effectiveRoles(Long userId) {
        List<Long> ids = userRoleMapper.selectList(new QueryWrapper<UserRole>().eq("user_id", userId))
                .stream().map(UserRole::getRoleId).toList();
        if (ids.isEmpty()) return List.of();
        return roleMapper.selectList(new QueryWrapper<Role>().in("id", ids).eq("status", "ENABLED"));
    }
}