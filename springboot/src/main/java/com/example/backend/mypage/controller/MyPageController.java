package com.example.backend.mypage.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.CommentItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.FavoriteItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.NotificationItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.PostItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.ReviewItem;
import com.example.backend.mypage.dto.response.MyPageOverviewResponse;
import com.example.backend.mypage.service.MyPageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mypage")
public class MyPageController {

    private final MyPageService myPageService;

    public MyPageController(MyPageService myPageService) {
        this.myPageService = myPageService;
    }

    @GetMapping("/overview")
    public ApiResponse<MyPageOverviewResponse> getOverview(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getOverview(account.accountId()));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteItem>> getFavorites(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getFavorites(account.accountId()));
    }

    @GetMapping("/restaurants")
    public ApiResponse<List<FavoriteItem>> getSavedRestaurants(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getFavorites(account.accountId()));
    }

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewItem>> getReviews(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getReviews(account.accountId()));
    }

    @GetMapping("/posts")
    public ApiResponse<List<PostItem>> getPosts(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getPosts(account.accountId()));
    }

    @GetMapping("/comments")
    public ApiResponse<List<CommentItem>> getComments(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getComments(account.accountId()));
    }

    @GetMapping("/notifications/unread")
    public ApiResponse<List<NotificationItem>> getUnreadNotifications(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getUnreadNotifications(account.accountId()));
    }
}
