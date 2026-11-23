package com.leis.permission.dept.vo;

import jakarta.validation.constraints.*;

public record DeptUpdateRequest(
        Long parentId,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(0) Integer sortOrder,
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
        @NotNull Integer version
) {
}
