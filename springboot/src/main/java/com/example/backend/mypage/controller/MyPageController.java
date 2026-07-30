package com.example.backend.mypage.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.mypage.dto.response.MyPageOverviewResponse;
import com.example.backend.mypage.service.MyPageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
