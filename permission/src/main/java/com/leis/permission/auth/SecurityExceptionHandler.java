package com.leis.permission.auth;

import com.leis.common.common.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将方法级权限检查产生的访问拒绝转换为 HTTP 错误。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SecurityExceptionHandler {
    /**
     * 将控制器执行期间的访问拒绝异常统一转换为 403 响应。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException error) {
        return ResponseEntity.status(403).body(ApiResponse.error(403, "没有操作权限"));
    }
}