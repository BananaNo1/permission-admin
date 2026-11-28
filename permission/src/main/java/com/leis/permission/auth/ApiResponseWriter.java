package com.leis.permission.auth;

import com.leis.common.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** 让 Spring Security 过滤链使用与控制器一致的 JSON 响应。 */
@Component
public class ApiResponseWriter {
    private final JsonMapper json;

    /** 复用应用的 Jackson 配置。 */
    public ApiResponseWriter(JsonMapper json) {
        this.json = json;
    }

    /** 写入 HTTP 状态及统一 JSON 数据。 */
    void write(HttpServletResponse response, ApiResponse<?> body) throws IOException {
        response.setStatus(body.code());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.writeValueAsString(body));
    }

    /** 写入安全过滤链中的认证、授权或限流错误。 */
    void error(HttpServletResponse response, int status, String message) throws IOException {
        write(response, ApiResponse.error(status, message));
    }
}
