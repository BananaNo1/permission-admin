package com.leis.permission.role.vo;

import com.leis.permission.role.entity.Role;

public record RoleVO(String id, String code, String name,
                     String status, String dataScope, Integer version) {

    /**
     * 转换角色实体，提供编码、状态、数据范围和版本。
     */
    public static RoleVO from(Role row) {
        return new RoleVO(
                (row.getId() == null ? null : row.getId().toString()),
                row.getCode(),
                row.getName(),
                row.getStatus(),
                row.getDataScope(),
                row.getVersion()
        );
    }
}
