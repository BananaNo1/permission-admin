package com.leis.permission.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leis.permission.role.entity.Role;
import com.leis.permission.role.mapper.RoleMapper;
import com.leis.permission.user.entity.User;
import com.leis.permission.user.entity.UserRole;
import com.leis.permission.user.mapper.UserMapper;
import com.leis.permission.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询账号及其生效角色，为授权决策和流程审批提供账号数据。
 */
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
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
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
        List<Long> ids = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().in(Role::getId, ids).eq(Role::getStatus, "ENABLED"));
    }

    /**
     * 批量查询账号的展示名称，避免审批详情按办理人逐条查询。
     */
    public Map<String, String> displayNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(ids).stream().collect(Collectors.toMap(
                user -> user.getId().toString(),
                user -> user.getNickname() == null || user.getNickname().isBlank()
                        ? user.getUsername() : user.getNickname()));
    }

}