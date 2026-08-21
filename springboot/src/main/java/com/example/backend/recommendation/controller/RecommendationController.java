package com.example.backend.recommendation.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.PersonalRecommendationResponse;
import com.example.backend.recommendation.dto.response.RecommendationPageResponse;
import com.example.backend.recommendation.dto.response.RestaurantRankResponse;
import com.example.backend.recommendation.service.RecommendationPreviewService;
import com.example.backend.recommendation.service.RecommendationService;

import java.util.List;

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

    @GetMapping
    public ApiResponse<RecommendationPageResponse> getRecommendations(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(required = false) String rankingSort
    ) {
        Long accountId = account != null ? account.accountId() : null;
        return ApiResponse.success(recommendationPreviewService.getPage(accountId, rankingSort));
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<NaturalLanguageRecommendationResponse>> recommendByQuery(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestBody NaturalLanguageRecommendationRequest request
    ) {
        NaturalLanguageRecommendationResponse response = recommendationService.recommendByQuery(request, account);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 💡 나를 위한 맛집 (Java 단독 연산 개인화 추천 API)
     */
    @GetMapping("/personal")
    public ResponseEntity<ApiResponse<PersonalRecommendationResponse>> getPersonalRecommendations(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(required = false, defaultValue = "37.4979") Double latitude,
            @RequestParam(required = false, defaultValue = "127.0276") Double longitude,
            @RequestParam(required = false, defaultValue = "3000") Double radiusMeters,
            @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        PersonalRecommendationResponse response = recommendationService.recommendForUser(
                account, latitude, longitude, radiusMeters, limit
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
    @GetMapping("/rankings")
    public ResponseEntity<List<RestaurantRankResponse>> getTopRankings(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false, defaultValue = "3000") Double radiusMeters,
            @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        List<RestaurantRankResponse> rankings = recommendationService.getTopRankedRestaurants(
                latitude, longitude, radiusMeters, limit
        );
        return ResponseEntity.ok(rankings);
    }
}
