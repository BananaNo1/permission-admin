package com.leis.common.common;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 按业务异常指定的状态码和提示构造响应。
     */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> business(BusinessException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 将数据库唯一值或引用约束冲突转为 409 响应。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(new ErrorResponse("唯一值冲突或记录仍被引用"));
    }

    /**
     * 将请求参数、字段格式和请求体解析错误转为 400 响应。
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ErrorResponse> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("请检查字段格式与必填项"));
    }

    /**
     * 记录未预期异常的详细日志，向调用方返回统一的服务错误。
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        LoggerFactory.getLogger(GlobalExceptionHandler.class).error("请求处理失败", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("服务暂时不可用，请查看后端日志"));
    }
}
