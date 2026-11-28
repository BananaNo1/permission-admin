package com.leis.common.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 通用分页响应，包含当前页记录、总数和分页参数。
 */
@Schema(description = "通用分页响应，包含当前页记录、总数和分页参数")
public record PageResult<T>(
    @Schema(description = "当前页的记录列表")
    List<T> records,
    @Schema(description = "符合查询条件的记录总数")
    long total,
    @Schema(description = "当前页码，从 1 开始")
    long page,
    @Schema(description = "每页记录数")
    long pageSize
) {
}
