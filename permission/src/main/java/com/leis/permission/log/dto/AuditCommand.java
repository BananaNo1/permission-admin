package com.leis.permission.log.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 描述一条待写入的操作审计记录及其请求追踪信息。
 */
@Schema(description = "操作审计写入参数")
public record AuditCommand(
        @Schema(description = "操作者用户主键，匿名操作时为空")
        Long actorId,
        @Schema(description = "操作者的登录账号或匿名标识")
        String actorUsername,
        @Schema(description = "被审计的操作类型")
        String action,
        @Schema(description = "操作目标的资源类型")
        String targetType,
        @Schema(description = "操作目标的资源标识")
        String targetId,
        @Schema(description = "操作是否成功")
        boolean success,
        @Schema(description = "操作结果或变更摘要")
        String summary,
        @Schema(description = "用于关联服务端日志的请求追踪标识")
        String traceId
) {
}
