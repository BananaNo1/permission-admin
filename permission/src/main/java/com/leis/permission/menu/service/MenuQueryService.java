package com.leis.permission.menu.service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.leis.permission.menu.entity.Menu;
import com.leis.permission.menu.mapper.MenuMapper;
import com.leis.permission.role.entity.RoleMenu;
import com.leis.permission.role.mapper.RoleMapper;
import com.leis.permission.role.mapper.RoleMenuMapper;
import com.leis.permission.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MenuQueryService {

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    /**
     * 按角色授权取出启用的菜单和操作，超级管理员跳过角色关联过滤。
     */
    public List<Menu> grantedMenus(List<Long> roleIds, boolean admin) {
        var all = menuMapper.selectList(new QueryWrapper<Menu>().orderByAsc("sort_order", "id"));
        Set<Long> granted = new HashSet<Long>();
        if (admin) {
            all.forEach(row -> granted.add(row.getId()));
        } else {
            if (roleIds.isEmpty()) {
                return List.of();
            }
            roleMenuMapper.selectList(new QueryWrapper<RoleMenu>()
                            .in("role_id", roleIds))
                    .forEach(row -> granted.add(row.getMenuId()));
        }
        var byId = new HashMap<Long, Menu>();
        all.forEach(row -> byId.put(row.getId(), row));
        return all.stream().filter(row -> menuActive(row, byId, granted)).toList();
    }

    /**
     * 沿祖先链核对菜单的授权和启用状态，同时防止循环或缺失的上级节点。
     */
    private boolean menuActive(Menu row, Map<Long, Menu> byId, Set<Long> granted) {
        var visited = new HashSet<Long>();
        while (row != null) {
            if (!visited.add(row.getId()) ||
                    !granted.contains(row.getId()) ||
                    !"ENABLED".equals(row.getStatus())) {
                return false;
            }
            if (row.getParentId() == null) {
                return true;
            }
            row = byId.get(row.getParentId());
            if (row == null) {
                return false;
            }
        }
        return false;
    }
}
