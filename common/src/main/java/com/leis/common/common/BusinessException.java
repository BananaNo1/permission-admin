package com.leis.common.common;

/**
 * 携带 HTTP 状态码与用户可读信息的业务异常。
 */
public class BusinessException extends RuntimeException{
    private final int status;

    /**
     * 返回接口应使用的业务错误状态码。
     */
    public int getStatus() {
        return status;
    }

    /**
     * 创建携带 HTTP 状态码和业务提示的异常。
     */
    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }
}
