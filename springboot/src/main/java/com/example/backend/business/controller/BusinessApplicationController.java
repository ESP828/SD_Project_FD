package com.example.backend.business.controller;

import com.example.backend.business.dto.request.BusinessApplicationRequest;
import com.example.backend.business.dto.response.BusinessApplicationResponse;
import com.example.backend.business.service.BusinessApplicationService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BusinessApplicationController {

    private final BusinessApplicationService businessApplicationService;

    public BusinessApplicationController(BusinessApplicationService businessApplicationService) {
        this.businessApplicationService = businessApplicationService;
    }

    @PostMapping("/business/applications")
    public ApiResponse<BusinessApplicationResponse> submitApplication(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody BusinessApplicationRequest request
    ) {
        return ApiResponse.success(businessApplicationService.submitApplication(account.accountId(), request));
    }

    @GetMapping("/business/applications")
    public ApiResponse<List<BusinessApplicationResponse>> getMyApplications(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(businessApplicationService.findMyApplications(account.accountId()));
    }

    @GetMapping("/admin/business-applications")
    public ApiResponse<List<BusinessApplicationResponse>> getAllApplications(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(businessApplicationService.findAllApplications());
    }

    @PatchMapping("/admin/business-applications/{applicationId}/approve")
    public ApiResponse<BusinessApplicationResponse> approveApplication(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long applicationId
    ) {
        return ApiResponse.success(businessApplicationService.approve(account.accountId(), applicationId));
    }

    @PatchMapping("/admin/business-applications/{applicationId}/reject")
    public ApiResponse<BusinessApplicationResponse> rejectApplication(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long applicationId,
            @RequestParam(required = false) String rejectReason
    ) {
        return ApiResponse.success(businessApplicationService.reject(account.accountId(), applicationId, rejectReason));
    }
}
