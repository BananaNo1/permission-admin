package com.leis.permission.role.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.permission.role.entity.Role;

/**
 * 返回角色展示、数据范围及版本校验所需的属性。
 */
@Schema(description = "返回角色展示、数据范围及版本校验所需的属性")
public record RoleVO(
    @Schema(description = "记录标识")
    String id,
    @Schema(description = "唯一角色编码")
    String code,
    @Schema(description = "显示名称")
    String name,
    @Schema(description = "启用状态，取值为 ENABLED 或 DISABLED")
    String status,
    @Schema(description = "数据范围，支持 ALL、DEPT、DEPT_AND_CHILDREN 和 SELF")
    String dataScope,
    @Schema(description = "用于并发校验的数据版本")
    Integer version
) {

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
