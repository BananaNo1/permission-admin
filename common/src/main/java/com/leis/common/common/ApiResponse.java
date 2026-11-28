package com.leis.common.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

/** 应用 JSON 接口统一使用的响应结构。 */
@Schema(description = "统一 API 响应")
public record ApiResponse<T>(
        @Schema(description = "HTTP 状态码") int code,
        @Schema(description = "操作结果或错误提示") String message,
        @Schema(description = "业务数据或校验详情，无内容时为 null") T data
) {
    /** 返回查询或操作成功后的数据。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), "成功", data);
    }

    /** 返回无业务数据的操作成功结果。 */
    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    /** 返回资源创建结果。 */
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), "成功", data);
    }

    /** 返回无附加数据的错误。 */
    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
