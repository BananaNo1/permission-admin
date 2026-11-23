package com.leis.permission.menu.dto;

import jakarta.validation.constraints.*;

public record MenuCreateRequest(
        Long parentId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 300) String description,
        @NotBlank @Pattern(regexp = "DIRECTORY|PAGE|BUTTON") String type,
        @Size(max = 100) String routeName,
        @Size(max = 200) String path,
        @Size(max = 100) String componentKey,
        @Size(max = 100) String permissionCode,
        @NotNull @Min(0) Integer sortOrder,
        @NotNull Boolean visible,
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status
) {
}