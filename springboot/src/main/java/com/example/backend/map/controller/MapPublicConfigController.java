package com.example.backend.map.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.map.dto.response.MapPublicConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 브라우저가 카카오맵 SDK를 로드할 때 필요한 공개 설정만 제공한다.
 *
 * <p>JavaScript 키는 브라우저에 노출되는 공개 식별자다. REST API 키나
 * 클라이언트 시크릿은 이 응답에 절대 포함하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/public/map")
public class MapPublicConfigController {

    private final String kakaoJavascriptKey;

    public MapPublicConfigController(
            @Value("${kakao.maps.javascript-key:}") String kakaoJavascriptKey
    ) {
        this.kakaoJavascriptKey = kakaoJavascriptKey;
    }

    @GetMapping("/config")
    public ApiResponse<MapPublicConfigResponse> config() {
        return ApiResponse.success(MapPublicConfigResponse.kakao(kakaoJavascriptKey));
    }
}
