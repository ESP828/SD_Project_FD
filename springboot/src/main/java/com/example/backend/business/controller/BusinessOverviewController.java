package com.example.backend.business.controller;

import com.example.backend.business.dto.response.BusinessOverviewResponse;
import com.example.backend.business.service.BusinessOverviewService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business")
public class BusinessOverviewController {

    private final BusinessOverviewService businessOverviewService;

    public BusinessOverviewController(BusinessOverviewService businessOverviewService) {
        this.businessOverviewService = businessOverviewService;
    }

    @GetMapping("/overview")
    public ApiResponse<BusinessOverviewResponse> getOverview(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(businessOverviewService.getOverview(account.accountId()));
    }
}
