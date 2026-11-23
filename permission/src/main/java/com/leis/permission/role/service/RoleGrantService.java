package com.leis.permission.role.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.leis.common.common.BusinessException;
import com.leis.permission.auth.PermissionChecker;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.menu.entity.Menu;
import com.leis.permission.menu.mapper.MenuMapper;
import com.leis.permission.role.dto.RoleGrantRequest;
import com.leis.permission.role.entity.Role;
import com.leis.permission.role.entity.RoleMenu;
import com.leis.permission.role.mapper.RoleMapper;
import com.leis.permission.role.mapper.RoleMenuMapper;
import com.leis.permission.support.VersionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleGrantService {

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final PermissionChecker permissions;
    private final AuditService audit;

    /**
     * 检查管理员身份和角色存在性，返回该角色已经配置的菜单 ID。
     */
    public List<String> menuIds(Long id) {
        permissions.requireAdmin();
        if (roleMapper.selectById(id) == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return roleMenuMapper.selectList(new QueryWrapper<RoleMenu>().eq("role_id", id))
                .stream().map(row -> row.getMenuId().toString()).toList();
    }


    /**
     * 锁定菜单树和目标角色，校验版本及祖先授权后整体替换角色的菜单关联。
     */
    @Transactional
    public void grant(Long id, RoleGrantRequest request) {
        permissions.requireAdmin();
        menuMapper.lockTree();
        Role role = roleMapper.lock(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        VersionGuard.check(role.getVersion(), request.version());
        var byId = new HashMap<Long, Menu>();
        menuMapper.selectList(null).forEach(row -> byId.put(row.getId(), row));
        for (Long menuId : request.menuIds()) {
            Menu menu = byId.get(menuId);
            if (menu == null) {
                throw new BusinessException(400, "菜单不存在");
            }
            if (menu.getParentId() != null && !request.menuIds().contains(menu.getParentId())) {
                throw new BusinessException(400, "请同时选择全部菜单祖先");
            }
        }
        roleMenuMapper.delete(new QueryWrapper<RoleMenu>().eq("role_id", id));
        for (Long menuId : request.menuIds()) {
            RoleMenu link = new RoleMenu();
            link.setRoleId(id);
            link.setMenuId(menuId);
            roleMenuMapper.insert(link);
        }
        VersionGuard.updated(roleMapper.updateById(role));
        audit.record("ROLE_GRANT", id);
    }
}
