package com.leis.permission.auth;


import com.leis.common.common.BusinessException;
import com.leis.permission.SessionUser;
import com.leis.permission.dept.service.DeptQueryService;
import com.leis.permission.menu.entity.Menu;
import com.leis.permission.menu.service.MenuQueryService;
import com.leis.permission.role.entity.Role;
import com.leis.permission.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorizationService {
    private final AccountQueryService accountQueryService;
    private final MenuQueryService menuQueryService;
    private final DeptQueryService deptQueryService;

    /**
     * 校验账号状态和会话版本，再合并启用角色授予的操作权限及部门数据范围。
     */
    public ActorSnapshot resolve(SessionUser principal) {
        User user = accountQueryService.findById(principal.id());
        if (user == null || !"ENABLED".equals(user.getStatus())
                || !Objects.equals(user.getAuthVersion(), principal.authVersion())) {
            throw new BusinessException(401, "登录状态已失效");
        }
        List<Role> assigned = accountQueryService.effectiveRoles(user.getId());
        boolean admin = assigned.stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getCode()));
        var permissions = new HashSet<String>();
        menuQueryService.grantedMenus(assigned.stream().map(Role::getId).toList(), admin)
                .stream().map(Menu::getPermissionCode)
                .filter(Objects::nonNull)
                .forEach(permissions::add);
        boolean all = admin || assigned.stream().anyMatch(role -> "ALL".equals(role.getDataScope()));
        boolean self = assigned.stream().anyMatch(role -> "SELF".equals(role.getDataScope()));
        var deptIds = new HashSet<Long>();
        for (Role role : assigned) {
            if ("DEPT".equals(role.getDataScope())) {
                deptIds.add(user.getDeptId());
            }
            if ("DEPT_AND_CHILDREN".equals(role.getDataScope())) {
                deptIds.addAll(deptQueryService.subtree(user.getDeptId()));
            }

        }
        return new ActorSnapshot(user.getId(), user.getUsername(), user.getDeptId(), admin,
                Set.copyOf(permissions), all, Set.copyOf(deptIds), self);
    }
}
