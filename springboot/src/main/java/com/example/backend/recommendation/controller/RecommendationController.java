package com.example.backend.recommendation.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.dto.response.RecommendationPageResponse;
import com.example.backend.recommendation.service.RecommendationPreviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationPreviewService recommendationService;

    public RecommendationController(RecommendationPreviewService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ApiResponse<RecommendationPageResponse> getRecommendations(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(recommendationService.getPage(account.accountId()));
    }
}
