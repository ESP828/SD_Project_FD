package com.example.backend.preset.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PresetCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 255) String summary,
        @Size(max = 5000) String description,
        @Size(max = 500) String imageUrl,
        @NotBlank @Size(max = 50) String category,
        Boolean isPublic
) {
    public boolean publicOrDefault() {
        return isPublic == null || isPublic;
    }
}
