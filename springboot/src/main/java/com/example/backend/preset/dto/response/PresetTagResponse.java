package com.example.backend.preset.dto.response;

public record PresetTagResponse(
        Integer tagId,
        String tagName,
        int displayOrder
) {
}
