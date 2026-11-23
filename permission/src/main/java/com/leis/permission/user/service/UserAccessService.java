package com.leis.permission.user.service;


import com.leis.common.common.BusinessException;
import com.leis.permission.auth.AccountQueryService;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.dept.mapper.DeptMapper;
import com.leis.permission.dept.service.DeptQueryService;
import com.leis.permission.support.VersionGuard;
import com.leis.permission.user.entity.User;
import com.leis.permission.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserAccessService {

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final UserScopeService scope;
    private final CurrentActor current;
    private final AccountQueryService accounts;
    private final DeptQueryService departments;

    /**
     * 按组织树、用户行的顺序加锁，再检查数据范围、版本及超级管理员保护规则。
     */
    public User writable(Long id, Integer version) {
        deptMapper.lockTree();
        User row = userMapper.lock(id);
        if (row == null || userMapper.selectCount(scope.scoped(current.get()).eq("id", id)) == 0) {
            throw new BusinessException(404, "用户不存在或不可访问");
        }
        VersionGuard.check(row.getVersion(), version);
        boolean administrator = accounts.effectiveRoles(id).stream()
                .anyMatch(role -> "SUPER_ADMIN".equals(role.getCode()));
        if (administrator && !current.get().admin()) {
            throw new BusinessException(403, "需要超级管理员权限");
        }
        return row;
    }

    /**
     * 校验目标部门及其上级链的启用状态，并限制用户变更在授权范围内。
     */
    public void targetDepartment(Long deptId, User original) {
        if (!departments.activeDept(deptId)) {
            throw new BusinessException(400, "请选择有效且启用的部门");
        }
        var actor = current.get();
        boolean ownDepartment = original != null && actor.includeSelf()
                && Objects.equals(original.getId(), actor.id()) && Objects.equals(original.getDeptId(), deptId);
        if (!actor.all() && !actor.deptIds().contains(deptId) && !ownDepartment) {
            throw new BusinessException(403, "目标部门超出授权范围");
        }
    }

}
