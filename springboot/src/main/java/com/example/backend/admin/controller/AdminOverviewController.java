package com.example.backend.admin.controller;

import com.example.backend.admin.dto.response.AdminOverviewResponse;
import com.example.backend.admin.service.AdminOverviewService;
import com.example.backend.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    public AdminOverviewController(AdminOverviewService adminOverviewService) {
        this.adminOverviewService = adminOverviewService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> getOverview() {
        return ApiResponse.success(adminOverviewService.getOverview());
    }
}
