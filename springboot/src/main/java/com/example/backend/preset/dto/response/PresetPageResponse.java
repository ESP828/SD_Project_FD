package com.example.backend.preset.dto.response;

import java.util.List;

public record PresetPageResponse(
        List<PresetSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public PresetPageResponse {
        content = List.copyOf(content);
    }
}
