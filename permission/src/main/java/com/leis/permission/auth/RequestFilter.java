package com.leis.permission.auth;

import com.leis.common.common.BusinessException;
import com.leis.permission.SessionUser;
import com.leis.permission.log.dto.AuditCommand;
import com.leis.permission.log.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为请求生成追踪标识，解析当前登录身份并记录失败写操作。
 */
public class RequestFilter extends OncePerRequestFilter {

    private final AuthorizationService identity;
    private final AuditService auditService;
    private final ApiResponseWriter responses;

    /**
     * 初始化请求身份解析及失败审计依赖。
     */
    public RequestFilter(AuthorizationService identity, AuditService auditService, ApiResponseWriter responses) {
        this.identity = identity;
        this.auditService = auditService;
        this.responses = responses;
    }

    /**
     * 为请求生成追踪 ID，重验登录会话并注入身份快照，结束时审计失败的非查询请求。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String trace = UUID.randomUUID().toString();
        request.setAttribute("traceId", trace);
        response.setHeader("X-Trace-Id", trace);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionUser principal) {
            try {
                request.setAttribute("actor", identity.resolve(principal));
            }
            catch (BusinessException e) {
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                SecurityContextHolder.clearContext();
                responses.error(response, 401, "登录状态已失效");
                return;
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (response.getStatus() >= 400 && !"GET".equals(request.getMethod())
                    && !request.getRequestURI().equals("/api/auth/login")) {
                String username = authentication == null ? "anonymous" : authentication.getName();
                try {
                    auditService.failure(new AuditCommand(null, username, request.getMethod(),
                            "HTTP", request.getRequestURI(), false,
                            "HTTP " + response.getStatus(), trace));
                } catch (RuntimeException e) {
                    logger.error("失败审计写入异常 " + trace, e);
                }
            }
        }
    }
}
