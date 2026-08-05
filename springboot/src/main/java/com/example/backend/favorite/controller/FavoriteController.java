package com.example.backend.favorite.controller;

import com.example.backend.favorite.service.FavoriteService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{restaurantId}/favorite")
    public ApiResponse<Boolean> toggleFavorite(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        boolean favorited = favoriteService.toggle(account.accountId(), restaurantId);
        return ApiResponse.success(favorited ? "찜 목록에 추가했습니다." : "찜 목록에서 제거했습니다.", favorited);
    }
}
