package com.leis.permission.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 返回登录审计的账号、结果、时间及追踪信息。
 */
@Schema(description = "返回登录审计的账号、结果、时间及追踪信息")
public record LoginLogVO(
    @Schema(description = "记录标识")
    String id,
    @Schema(description = "登录账号")
    String username,
    @Schema(description = "该操作是否成功")
    boolean success,
    @Schema(description = "登录请求的来源 IP")
    String ip,
    @Schema(description = "用于关联服务端日志的请求追踪标识")
    String traceId,
    @Schema(description = "审计记录生成时间")
    String createdAt
) {
}
