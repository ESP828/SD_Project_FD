package com.example.backend.admin.controller;

import com.example.backend.admin.dto.request.AdminAccountUpdateRequest;
import com.example.backend.admin.dto.response.AdminAccountResponse;
import com.example.backend.admin.service.AdminAccountService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.response.PageResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminAccountResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ApiResponse.success(adminAccountService.search(keyword, role, page, size));
    }

    @PatchMapping("/{accountId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long accountId,
            @Valid @RequestBody AdminAccountUpdateRequest request
    ) {
        adminAccountService.update(account.accountId(), accountId, request);
        return ApiResponse.success("계정 정보를 수정했습니다.", null);
    }

    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long accountId
    ) {
        adminAccountService.delete(account.accountId(), accountId);
        return ApiResponse.success("계정을 탈퇴 처리했습니다.", null);
    }
}
