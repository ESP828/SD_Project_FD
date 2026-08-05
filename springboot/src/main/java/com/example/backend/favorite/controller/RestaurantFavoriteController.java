package com.example.backend.favorite.controller;

import com.example.backend.favorite.dto.response.RestaurantFavoriteStateResponse;
import com.example.backend.favorite.service.RestaurantFavoriteService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantFavoriteController {

    private final RestaurantFavoriteService favoriteService;

    public RestaurantFavoriteController(RestaurantFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{restaurantId}/favorite")
    public ApiResponse<RestaurantFavoriteStateResponse> add(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(
                "음식점을 저장했습니다.",
                favoriteService.add(restaurantId, account.accountId())
        );
    }

    @DeleteMapping("/{restaurantId}/favorite")
    public ApiResponse<RestaurantFavoriteStateResponse> remove(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(
                "음식점 저장을 해제했습니다.",
                favoriteService.remove(restaurantId, account.accountId())
        );
    }
}
