package com.example.backend.news.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.news.dto.request.RestaurantNewsCreateRequest;
import com.example.backend.news.dto.response.RestaurantNewsResponse;
import com.example.backend.news.service.RestaurantNewsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가게 사장님(음식점 등록 계정)만 자기 가게에 소식을 올릴 수 있는 API.
 */
@RestController
@RequestMapping("/api/restaurants")
public class RestaurantNewsController {

    private final RestaurantNewsService restaurantNewsService;

    public RestaurantNewsController(RestaurantNewsService restaurantNewsService) {
        this.restaurantNewsService = restaurantNewsService;
    }

    @PostMapping("/{restaurantId}/news")
    public ApiResponse<RestaurantNewsResponse> createNews(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody RestaurantNewsCreateRequest request
    ) {
        return ApiResponse.success(
                "소식이 등록되었습니다.",
                restaurantNewsService.createNews(restaurantId, account.accountId(), request)
        );
    }
}
