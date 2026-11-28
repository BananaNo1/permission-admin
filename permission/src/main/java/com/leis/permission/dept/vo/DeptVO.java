package com.leis.permission.dept.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.permission.dept.entity.Dept;

/**
 * 返回部门节点及树结构展示所需的字段。
 */
@Schema(description = "返回部门节点及树结构展示所需的字段")
public record DeptVO(
    @Schema(description = "记录标识")
    String id,
    @Schema(description = "上级节点标识，根节点没有上级")
    String parentId,
    @Schema(description = "显示名称")
    String name,
    @Schema(description = "同级节点排序值，数值越小越靠前")
    Integer sortOrder,
    @Schema(description = "启用状态，取值为 ENABLED 或 DISABLED")
    String status,
    @Schema(description = "用于并发校验的数据版本")
    Integer version,
    @Schema(description = "当前操作是否允许选择该部门")
    boolean selectable,
    @Schema(description = "配置的部门负责人用户标识")
    String managerUserId
) {

    /**
     * 转换部门实体并标明该节点在当前操作中能否选择。
     */
    public static DeptVO from(Dept row, boolean selectable) {
        return new DeptVO(row.getId().toString(),
                row.getParentId() == null ? null : row.getParentId().toString(),
                row.getName(), row.getSortOrder(), row.getStatus(), row.getVersion(), selectable,
                row.getManagerUserId() == null ? null : row.getManagerUserId().toString());
    }
}
