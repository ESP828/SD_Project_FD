package com.example.backend.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminPresetUpsertRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 50) String category,
        @PositiveOrZero Integer displayOrder,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE|DELETED") String status
) {
}
