package com.leis.common.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将业务、参数校验和框架异常转换为统一 API 响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 按业务异常指定的状态码和提示构造响应。 */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return error(e.getStatus(), e.getMessage());
    }

    /** 将数据库约束冲突转为 HTTP 409。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException e) {
        return error(409, "唯一值冲突或记录仍被引用");
    }

    /** 返回字段校验失败的具体原因。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<ValidationDetails>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (var fieldError : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String message = fields.isEmpty() ? "请检查字段格式与必填项" : fields.values().iterator().next();
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, message, new ValidationDetails(fields)));
    }

    /** 处理格式错误、参数类型不匹配及缺少必填参数的请求。 */
    @ExceptionHandler({ IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
    ResponseEntity<ApiResponse<Void>> invalid(Exception e) {
        return error(400, "请检查请求格式与必填项");
    }

    /** 保留框架异常的状态和响应头，并记录其他未预期异常。 */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        if (e instanceof ErrorResponse framework) {
            int code = framework.getStatusCode().value();
            String message = switch (code) {
                case 404 -> "接口不存在";
                case 405 -> "该接口不支持此请求方法";
                case 415 -> "不支持的请求内容类型";
                default -> "请求处理失败";
            };
            return ResponseEntity.status(framework.getStatusCode()).headers(framework.getHeaders())
                    .body(ApiResponse.error(code, message));
        }
        LoggerFactory.getLogger(GlobalExceptionHandler.class).error("请求处理失败", e);
        return error(500, "服务暂时不可用，请查看后端日志");
    }

    /** 以同一个状态码设置 HTTP 状态和响应字段。 */
    private static ResponseEntity<ApiResponse<Void>> error(int code, String message) {
        return ResponseEntity.status(code).body(ApiResponse.error(code, message));
    }

    /** 记录请求字段与对应的校验错误。 */
    @Schema(description = "请求参数校验详情")
    public record ValidationDetails(
            @Schema(description = "字段名与校验错误消息") Map<String, String> fieldErrors
    ) {}
}
