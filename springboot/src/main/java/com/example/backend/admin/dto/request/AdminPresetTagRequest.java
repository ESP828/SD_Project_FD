package com.example.backend.admin.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

public record AdminPresetTagRequest(
        @NotNull @Positive Integer tagId,
        @PositiveOrZero Integer displayOrder
) {
}
