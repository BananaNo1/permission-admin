package com.leis.permission.role.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.leis.common.common.BusinessException;
import com.leis.permission.auth.PermissionChecker;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.role.dto.RoleCreateRequest;
import com.leis.permission.role.dto.RoleStatusRequest;
import com.leis.permission.role.dto.RoleUpdateRequest;
import com.leis.permission.role.entity.Role;
import com.leis.permission.role.mapper.RoleMapper;
import com.leis.permission.role.mapper.RoleMenuMapper;
import com.leis.permission.role.vo.RoleVO;
import com.leis.permission.support.VersionGuard;
import com.leis.permission.user.entity.UserRole;
import com.leis.permission.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionChecker permissionChecker;
    private final AuditService auditService;

    /**
     * 仅向管理员返回按 ID 倒序排列的角色列表。
     */
    public List<RoleVO> list() {
        permissionChecker.requireAdmin();
        return roleMapper.selectList(new QueryWrapper<Role>().orderByDesc("id"))
                .stream().map(RoleVO::from).toList();
    }

    /**
     * 创建启用的自定义角色并初始化数据范围和并发版本。
     */
    @Transactional
    public RoleVO create(RoleCreateRequest request) {
        permissionChecker.requireAdmin();
        Role row = new Role();
        row.setCode(request.code());
        row.setName(request.name());
        row.setDataScope(request.dataScope());
        row.setStatus("ENABLED");
        row.setBuiltIn(false);
        row.setVersion(0);
        roleMapper.insert(row);
        auditService.record("ROLE_CREATE", row.getId());
        return RoleVO.from(row);
    }

    /**
     * 锁定并校验角色版本后修改名称和数据范围，内置角色保留全部范围。
     */
    @Transactional
    public RoleVO update(Long id, RoleUpdateRequest request) {
        permissionChecker.requireAdmin();
        Role row = locked(id, request.version());
        if (row.getBuiltIn() && !"ALL".equals(request.dataScope())) {
            throw new BusinessException(400, "内置角色保持全部范围");
        }
        row.setName(request.name());
        row.setDataScope(request.dataScope());
        VersionGuard.updated(roleMapper.updateById(row));
        auditService.record("ROLE_UPDATE", id);
        return RoleVO.from(row);
    }


    /**
     * 按版本修改角色的启停状态，保护内置角色保持启用。
     */
    @Transactional
    public void changeStatus(Long id, RoleStatusRequest request) {
        permissionChecker.requireAdmin();
        Role row = locked(id, request.version());
        if (row.getBuiltIn() && !"ENABLED".equals(request.status())) {
            throw new BusinessException(400, "内置角色保持启用");
        }
        row.setStatus(request.status());
        VersionGuard.updated(roleMapper.updateById(row));
        auditService.record("ROLE_STATUS", id);
    }

    /**
     * 拒绝删除内置角色或仍与用户关联的角色，删除成功后记录审计。
     */
    @Transactional
    public void delete(Long id, Integer version) {
        permissionChecker.requireAdmin();
        Role row = locked(id, version);
        if (row.getBuiltIn()) throw new BusinessException(400, "内置角色不能删除");
        if (userRoleMapper.selectCount(new QueryWrapper<UserRole>().eq("role_id", id)) > 0) {
            throw new BusinessException(409, "请先解除用户角色关联");
        }

        VersionGuard.updated(roleMapper.delete(new QueryWrapper<Role>().eq("id", id).eq("version", version)));
        auditService.record("ROLE_DELETE", id);
    }

    /**
     * 读取并锁定待更新的角色，同时校验记录是否存在及版本是否一致。
     */
    private Role locked(Long id, Integer version) {
        Role row = roleMapper.lock(id);
        if (row == null) {
            throw new BusinessException(404, "角色不存在");
        }
        VersionGuard.check(row.getVersion(), version);
        return row;
    }

}
