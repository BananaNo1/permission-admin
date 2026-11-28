package com.leis.permission.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leis.common.common.BusinessException;
import com.leis.permission.auth.PermissionChecker;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.role.entity.Role;
import com.leis.permission.role.mapper.RoleMapper;
import com.leis.permission.support.VersionGuard;
import com.leis.permission.user.dto.UserRoleAssignRequest;
import com.leis.permission.user.entity.UserRole;
import com.leis.permission.user.mapper.UserMapper;
import com.leis.permission.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 维护用户角色关联，校验角色有效性及超级管理员保护规则。
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final UserAccessService access;
    private final PermissionChecker permissions;
    private final AuditService audit;

    /**
     * 校验管理员身份和目标账号存在性，返回已分配的角色 ID。
     */
    public List<String> roleIds(Long userId) {
        permissions.requireAdmin();
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(row -> row.getRoleId().toString()).toList();
    }

    /**
     * 校验目标账号和角色集合，在同一事务中整体替换关联，并为初始管理员保留内置角色。
     */
    @Transactional
    public void assign(Long userId, UserRoleAssignRequest request) {
        permissions.requireAdmin();
        var user = access.writable(userId, request.version());
        for (Long roleId : request.roleIds())
            if (roleMapper.selectById(roleId) == null) {
                throw new BusinessException(400, "角色不存在");
            }
        if (user.getProtectedAccount()) {
            Role root = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "SUPER_ADMIN"));
            if (!request.roleIds().contains(root.getId())) {
                throw new BusinessException(400, "初始管理员必须保留内置角色");
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Long roleId : request.roleIds()) {
            UserRole link = new UserRole();
            link.setUserId(userId);
            link.setRoleId(roleId);
            userRoleMapper.insert(link);
        }
        VersionGuard.updated(userMapper.updateById(user));
        audit.record("USER_ROLE_ASSIGN", userId);
    }

}
