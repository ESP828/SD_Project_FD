package com.example.backend.mypage.controller;

import com.example.backend.auth.dto.request.ChangePasswordRequest;
import com.example.backend.auth.service.AuthService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.response.PageResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.mypage.dto.request.MyPageProfileUpdateRequest;
import com.example.backend.mypage.dto.request.WithdrawAccountRequest;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.CommentItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.FavoriteItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.NotificationItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.PostItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.ReviewItem;
import com.example.backend.mypage.dto.response.MyPageOverviewResponse;
import com.example.backend.mypage.service.MyPageAccountService;
import com.example.backend.mypage.service.MyPageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mypage")
public class MyPageController {

    private final MyPageService myPageService;
    private final MyPageAccountService myPageAccountService;
    private final AuthService authService;

    public MyPageController(
            MyPageService myPageService,
            MyPageAccountService myPageAccountService,
            AuthService authService
    ) {
        this.myPageService = myPageService;
        this.myPageAccountService = myPageAccountService;
        this.authService = authService;
    }

    @GetMapping("/overview")
    public ApiResponse<MyPageOverviewResponse> getOverview(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getOverview(account.accountId()));
    }

    @PatchMapping("/profile")
    public ApiResponse<MyPageOverviewResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody MyPageProfileUpdateRequest request
    ) {
        return ApiResponse.success(
                "내 정보가 수정되었습니다.",
                myPageService.updateProfile(account.accountId(), request)
        );
    }

    @GetMapping("/favorites")
    public ApiResponse<PageResponse<FavoriteItem>> getFavorites(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ApiResponse.success(myPageService.getFavorites(account.accountId(), page, size));
    }

    @GetMapping("/restaurants")
    public ApiResponse<PageResponse<FavoriteItem>> getSavedRestaurants(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ApiResponse.success(myPageService.getFavorites(account.accountId(), page, size));
    }

    @GetMapping("/reviews")
    public ApiResponse<PageResponse<ReviewItem>> getReviews(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ApiResponse.success(myPageService.getReviews(account.accountId(), page, size));
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostItem>> getPosts(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ApiResponse.success(myPageService.getPosts(account.accountId(), page, size));
    }

    @GetMapping("/comments")
    public ApiResponse<PageResponse<CommentItem>> getComments(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ApiResponse.success(myPageService.getComments(account.accountId(), page, size));
    }

    @GetMapping("/notifications/unread")
    public ApiResponse<List<NotificationItem>> getUnreadNotifications(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(myPageService.getUnreadNotifications(account.accountId()));
    }

    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(account.accountId(), request);
        return ApiResponse.success("비밀번호가 변경되었습니다.", null);
    }

    @PatchMapping("/account/withdraw")
    public ApiResponse<Void> withdrawAccount(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody WithdrawAccountRequest request
    ) {
        myPageAccountService.withdraw(account.accountId(), request);
        return ApiResponse.success("회원 탈퇴가 완료되었습니다.", null);
    }
}
