package com.leis.permission.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leis.common.common.BusinessException;
import com.leis.permission.auth.PermissionChecker;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.menu.dto.MenuCreateRequest;
import com.leis.permission.menu.dto.MenuUpdateRequest;
import com.leis.permission.menu.entity.Menu;
import com.leis.permission.menu.mapper.MenuMapper;
import com.leis.permission.menu.vo.MenuVO;
import com.leis.permission.support.VersionGuard;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 维护目录、页面和权限项，校验层级、路由与并发修改约束。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuMapper menuMapper;
    private final PermissionChecker permissions;
    private final AuditService audit;


    /**
     * 检查管理员身份后，按配置排序和 ID 返回菜单维护列表。
     */
    public List<MenuVO> list() {
        permissions.requireAdmin();
        return menuMapper.selectList(new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSortOrder, Menu::getId)).stream().map(MenuVO::from).toList();
    }


    /**
     * 锁定菜单树，校验层级和路由配置后创建菜单及用途说明，并记录审计。
     */
    @Transactional
    public MenuVO create(MenuCreateRequest request) {
        permissions.requireAdmin();
        menuMapper.lockTree();
        Menu row = new Menu();
        row.setVersion(0);
        row.setParentId(request.parentId());
        row.setName(request.name());
        row.setDescription(StringUtils.hasText(request.description()) ? request.description().strip() : null);
        row.setType(request.type());
        row.setRouteName(request.routeName());
        row.setPath(request.path());
        row.setComponentKey(request.componentKey());
        row.setPermissionCode(request.permissionCode());
        row.setSortOrder(request.sortOrder());
        row.setVisible(request.visible());
        row.setStatus(request.status());
        validate(row, null);
        menuMapper.insert(row);
        audit.record("MENU_CREATE", row.getId());
        return MenuVO.from(row);
    }

    /**
     * 锁定菜单树并校验版本。用途说明省略时保留旧值，传空白时清除。
     */
    @Transactional
    public MenuVO update(Long id, MenuUpdateRequest request) {
        permissions.requireAdmin();
        menuMapper.lockTree();
        Menu row = menuMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "菜单不存在");
        }
        VersionGuard.check(row.getVersion(), request.version());
        String previousType = row.getType();
        row.setParentId(request.parentId());
        row.setName(request.name());
        if (request.description() != null) {
            row.setDescription(StringUtils.hasText(request.description()) ? request.description().strip() : null);
        }
        row.setType(request.type());
        row.setRouteName(request.routeName());
        row.setPath(request.path());
        row.setComponentKey(request.componentKey());
        row.setPermissionCode(request.permissionCode());
        row.setSortOrder(request.sortOrder());
        row.setVisible(request.visible());
        row.setStatus(request.status());
        validate(row, previousType);
        VersionGuard.updated(menuMapper.updateById(row));
        audit.record("MENU_UPDATE", id);
        return MenuVO.from(row);
    }

    /**
     * 校验菜单版本和子节点，保护内置系统目录，并在事务内删除和审计。
     */
    @Transactional
    public void delete(Long id, Integer version) {
        permissions.requireAdmin();
        menuMapper.lockTree();
        Menu row = menuMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "菜单不存在");
        }
        VersionGuard.check(row.getVersion(), version);
        if (id.equals(1L)) {
            throw new BusinessException(400, "系统目录不能删除");
        }
        if (menuMapper.selectCount(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, id)) > 0) {
            throw new BusinessException(409, "请先删除子菜单");
        }
        VersionGuard.updated(menuMapper.delete(new LambdaQueryWrapper<Menu>().eq(Menu::getId, id).eq(Menu::getVersion, version)));
        audit.record("MENU_DELETE", id);
    }

    /**
     * 校验父子层级、循环引用、子节点换型及页面白名单，并清理与当前类型无关的字段。
     */
    private void validate(Menu row, String previousType) {
        if (Objects.equals(row.getId(), 1L) && (row.getParentId() != null || !"DIRECTORY".equals(row.getType()))) {
            throw new BusinessException(400, "系统目录保持根节点和目录类型");
        }
        if (row.getParentId() == null) {
            if (!"DIRECTORY".equals(row.getType())) {
                throw new BusinessException(400, "根节点必须是目录");
            }
        } else {
            Menu parent = menuMapper.selectById(row.getParentId());
            if (parent == null) {
                throw new BusinessException(400, "父菜单不存在");
            }
            String expected = "BUTTON".equals(row.getType()) ? "PAGE" : "DIRECTORY";
            if (!expected.equals(parent.getType())) {
                throw new BusinessException(400, "父菜单类型无效");
            }
            Set<Long> visited = new HashSet<>();
            while (parent != null) {
                if (!visited.add(parent.getId()) || Objects.equals(parent.getId(), row.getId())) {
                    throw new BusinessException(400, "菜单不能形成循环");
                }
                parent = parent.getParentId() == null ? null : menuMapper.selectById(parent.getParentId());
            }
        }
        if (previousType != null && !previousType.equals(row.getType())
                && menuMapper.selectCount(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, row.getId())) > 0) {
            throw new BusinessException(409, "请先移除子节点再修改类型");
        }
        if (!"DIRECTORY".equals(row.getType()) && (row.getPermissionCode() == null
                || !row.getPermissionCode().matches("[a-z][a-z0-9:-]{2,99}"))) {
            throw new BusinessException(400, "请填写有效权限标识");
        }
        if ("PAGE".equals(row.getType())) {
            if (row.getComponentKey() == null || !Set.of("system/user", "system/role", "system/dept",
                    "system/menu", "audit/login", "audit/operation", "workflow/templates",
                    "workflow/tasks", "workflow/completed", "workflow/applications", "workflow/operations").contains(row.getComponentKey())) {
                throw new BusinessException(400, "页面组件键无效");
            }
            if (row.getPath() == null || !row.getPath().matches("/(system|audit|workflow)/[a-z-]+")
                    || row.getRouteName() == null || !row.getRouteName().matches("[A-Za-z][A-Za-z0-9]{1,99}")) {
                throw new BusinessException(400, "路由路径或名称无效");
            }
            var duplicate = new LambdaQueryWrapper<Menu>().eq(Menu::getPath, row.getPath());
            if (row.getId() != null) {
                duplicate.ne(Menu::getId, row.getId());
            }
            if (menuMapper.selectCount(duplicate) > 0) {
                throw new BusinessException(409, "路由路径重复");
            }
        } else {
            row.setRouteName(null);
            row.setPath(null);
            row.setComponentKey(null);
        }
        if ("DIRECTORY".equals(row.getType())) {
            row.setPermissionCode(null);
        }
    }
}
