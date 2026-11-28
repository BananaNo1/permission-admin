package com.leis.permission.dept.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/** 修改部门负责人，空标识表示清除。 */
@Schema(description = "部门负责人设置")
public record DeptManagerAssignment(
        @Schema(description = "启用的本部门用户标识，空值清除负责人")
        @Positive Long userId
) {}
