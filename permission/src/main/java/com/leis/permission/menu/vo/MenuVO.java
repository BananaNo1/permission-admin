package com.leis.permission.menu.vo;

import com.leis.permission.menu.entity.Menu;

public record MenuVO(String id, String parentId, String name, String description, String type, String routeName, String path, String componentKey, String permissionCode, Integer sortOrder, Boolean visible, String status, Integer version) {

    /**
     * 转换菜单配置、权限标识和用途说明，并以字符串传输主键。
     */
    public static MenuVO from(Menu row) {
        return new MenuVO(
                (row.getId() == null ? null : row.getId().toString()),
                (row.getParentId() == null ? null : row.getParentId().toString()),
                row.getName(),
                row.getDescription(),
                row.getType(),
                row.getRouteName(),
                row.getPath(),
                row.getComponentKey(),
                row.getPermissionCode(),
                row.getSortOrder(),
                row.getVisible(),
                row.getStatus(),
                row.getVersion()
        );
    }
}
