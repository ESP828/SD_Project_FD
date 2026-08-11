package com.example.backend.recommendation.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.RecommendationPageResponse;
import com.example.backend.recommendation.service.RecommendationPreviewService;
import com.example.backend.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationPreviewService recommendationPreviewService;
    private final RecommendationService recommendationService;

    // 두 의존성을 모두 주입받도록 생성자 수정
    public RecommendationController(
            RecommendationPreviewService recommendationPreviewService,
            RecommendationService recommendationService
    ) {
        this.recommendationPreviewService = recommendationPreviewService;
        this.recommendationService = recommendationService;
    }

    /**
     * 기존 추천 목록 조회 API
     */
    @GetMapping
    public ApiResponse<RecommendationPageResponse> getRecommendations(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(required = false) String rankingSort
    ) {
        return ApiResponse.success(recommendationPreviewService.getPage(account.accountId(), rankingSort));
    }

    /**
     * 자연어 기반 맛집 추천 API
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<NaturalLanguageRecommendationResponse>> recommendByQuery(
            @RequestBody NaturalLanguageRecommendationRequest request
    ) {
        NaturalLanguageRecommendationResponse response = recommendationService.recommendByQuery(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
