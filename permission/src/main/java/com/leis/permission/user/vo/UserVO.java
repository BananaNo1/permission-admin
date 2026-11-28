package com.leis.permission.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.leis.permission.user.entity.User;

/**
 * 返回用户展示和并发修改所需的字段，排除密码摘要。
 */
@Schema(description = "返回用户展示和并发修改所需的字段，排除密码摘要")
public record UserVO(
    @Schema(description = "记录标识")
    String id,
    @Schema(description = "登录账号")
    String username,
    @Schema(description = "用户显示名称")
    String nickname,
    @Schema(description = "所属部门标识")
    String deptId,
    @Schema(description = "启用状态，取值为 ENABLED 或 DISABLED")
    String status,
    @Schema(description = "用于并发校验的数据版本")
    Integer version
) {

    /**
     * 转换可返回给前端的用户资料和并发版本，不暴露密码摘要。
     */
    public static UserVO from(User row) {
        return new UserVO(
                (row.getId() == null ? null : row.getId().toString()),
                row.getUsername(),
                row.getNickname(),
                (row.getDeptId() == null ? null : row.getDeptId().toString()),
                row.getStatus(),
                row.getVersion()
        );
    }


}
