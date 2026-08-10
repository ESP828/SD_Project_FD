package com.example.backend.map.controller;

import com.example.backend.favorite.dto.response.RestaurantFavoriteStateResponse;
import com.example.backend.favorite.service.PublicRestaurantFavoriteService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공공데이터 출처 음식점에 로그인한 사용자가 찜하기를 남기는 API.
 * 조회는 {@code /api/public/map/restaurants/{id}}(인증 불필요, 로그인 시 찜 여부 포함)를 쓰고,
 * 등록/해제는 로그인이 필요해 permitAll 대상인 /api/public/** 밖(/api/map/**)에 둔다.
 */
@RestController
@RequestMapping("/api/map/restaurants")
public class MapRestaurantFavoriteController {

    private final PublicRestaurantFavoriteService favoriteService;

    public MapRestaurantFavoriteController(PublicRestaurantFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<RestaurantFavoriteStateResponse> add(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success("음식점을 저장했습니다.", favoriteService.add(id, account.accountId()));
    }

    @DeleteMapping("/{id}/favorite")
    public ApiResponse<RestaurantFavoriteStateResponse> remove(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success("음식점 저장을 해제했습니다.", favoriteService.remove(id, account.accountId()));
    }
}
