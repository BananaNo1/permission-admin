package com.leis.permission.dept.vo;

import com.leis.permission.dept.entity.Dept;

public record DeptVO(String id, String parentId, String name, Integer sortOrder,
                     String status, Integer version, boolean selectable) {

    /**
     * 转换部门实体并标明该节点在当前操作中能否选择。
     */
    public static DeptVO from(Dept row, boolean selectable) {
        return new DeptVO(row.getId().toString(),
                row.getParentId() == null ? null : row.getParentId().toString(),
                row.getName(), row.getSortOrder(), row.getStatus(), row.getVersion(), selectable);
    }
}
