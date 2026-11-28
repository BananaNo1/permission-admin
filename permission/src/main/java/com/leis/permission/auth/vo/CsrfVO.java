package com.leis.permission.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 返回写请求需要提交的 CSRF 请求头名称和令牌。
 */
@Schema(description = "返回写请求需要提交的 CSRF 请求头名称和令牌")
public record CsrfVO(
    @Schema(description = "本次会话使用的 CSRF 令牌")
    String token,
    @Schema(description = "写请求携带 CSRF 令牌的请求头名称")
    String headerName
)  {
}
