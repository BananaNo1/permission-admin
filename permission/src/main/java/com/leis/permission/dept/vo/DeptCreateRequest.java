package com.leis.permission.dept.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeptCreateRequest(
        Long parentId,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(0) Integer sortOrder
) {
}