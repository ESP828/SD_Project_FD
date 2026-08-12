package com.example.backend.recommendation.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.PersonalRecommendationResponse;
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
        Long accountId = account != null ? account.accountId() : null;
        return ApiResponse.success(recommendationPreviewService.getPage(accountId, rankingSort));
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

    /**
     * 💡 나를 위한 맛집 (개인화 추천 API)
     */
    @GetMapping("/personal")
    public ResponseEntity<ApiResponse<PersonalRecommendationResponse>> getPersonalRecommendations(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(required = false, defaultValue = "37.4979") Double latitude,
            @RequestParam(required = false, defaultValue = "127.0276") Double longitude,
            @RequestParam(required = false, defaultValue = "3000") Double radiusMeters,
            @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        Long accountId = account != null ? account.accountId() : null;

        PersonalRecommendationResponse response = recommendationService.recommendForUser(
                accountId, latitude, longitude, radiusMeters, limit
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
