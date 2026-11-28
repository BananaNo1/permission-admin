package com.leis.permission.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 返回操作审计的操作者、目标、结果及追踪信息。
 */
@Schema(description = "返回操作审计的操作者、目标、结果及追踪信息")
public record OperationLogVO(
    @Schema(description = "记录标识")
    String id,
    @Schema(description = "发起操作的登录账号")
    String actorUsername,
    @Schema(description = "执行的操作类型")
    String action,
    @Schema(description = "操作目标的资源类型")
    String targetType,
    @Schema(description = "操作目标的资源标识")
    String targetId,
    @Schema(description = "该操作是否成功")
    boolean success,
    @Schema(description = "操作结果或变更内容摘要")
    String summary,
    @Schema(description = "用于关联服务端日志的请求追踪标识")
    String traceId,
    @Schema(description = "审计记录生成时间")
    String createdAt
) {
}