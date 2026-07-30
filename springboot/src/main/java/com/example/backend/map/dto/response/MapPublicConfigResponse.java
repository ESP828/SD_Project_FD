package com.example.backend.map.dto.response;

public record MapPublicConfigResponse(
        String provider,
        String javascriptKey,
        boolean configured
) {
    public static MapPublicConfigResponse kakao(String javascriptKey) {
        String normalizedKey = javascriptKey == null ? "" : javascriptKey.trim();
        return new MapPublicConfigResponse(
                "KAKAO",
                normalizedKey,
                !normalizedKey.isEmpty()
        );
    }
}
