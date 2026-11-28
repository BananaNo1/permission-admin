package com.leis.permission.auth;

import com.leis.permission.log.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 限制连续失败的登录尝试，并在成功登录后清理失败记录。
 */
@Component
public class LoginThrottle {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuditService auditService;
    private final DefaultRedisScript<Long> increment = new DefaultRedisScript<>(
            "local n = redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],900) end;return n",
            Long.class);

    /**
     * 初始化基于 Redis 的登录失败计数及审计依赖。
     */
    public LoginThrottle(StringRedisTemplate stringRedisTemplate, AuditService auditService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.auditService = auditService;
    }

    /**
     * 按计数维度生成 Redis 键，对账号或来源 IP 做摘要后再拼接。
     */
    private String key(String kind, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "permission-admin:login:" + kind + ":" +
                    HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 将登录账号去除首尾空白并转为小写，与认证时的账号归一化保持一致。
     */
    private String username(HttpServletRequest request) {
        String value = request.getParameter("username");
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 检查指定计数是否已达到登录失败阈值。
     */
    private boolean limited(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        return value != null && Long.parseLong(value) >= 5;
    }

    /**
     * 原子递增账号和来源 IP 各自的失败计数，首次失败时设置 15 分钟期限。
     */
    public void failed(HttpServletRequest request) {
        stringRedisTemplate.execute(increment, List.of(key("user", username(request))));
        stringRedisTemplate.execute(increment, List.of(key("ip", request.getRemoteAddr())));
    }

    /**
     * 登录成功后清除该账号的失败次数，IP 计数按原期限到期。
     */
    public void succeeded(HttpServletRequest request) {
        stringRedisTemplate.delete(key("user", username(request)));
    }


    /**
     * 构建在密码认证前执行的登录限流过滤器。
     */
    public OncePerRequestFilter filter(ApiResponseWriter responses) {
        return new OncePerRequestFilter() {

            /**
             * 拦截超限的登录请求，记录审计并返回 429。
             */
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                if (request.getRequestURI().equals("/api/auth/login")
                        && request.getMethod().equals("POST")
                        && (limited(key("user", username(request)))
                        || limited(key("ip", request.getRemoteAddr())))) {
                    String name = username(request);
                    auditService.login(name.substring(0, Math.min(name.length(), 32)), null, false,
                            request.getRemoteAddr(), (String)request.getAttribute("traceId"));
                    responses.error(response, 429, "登录尝试过多，请15分钟后重试");
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };
    }

}
