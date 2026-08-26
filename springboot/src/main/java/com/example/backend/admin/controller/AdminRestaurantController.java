package com.example.backend.admin.controller;

import com.example.backend.admin.dto.request.AdminRestaurantUpdateRequest;
import com.example.backend.admin.dto.response.AdminRestaurantResponse;
import com.example.backend.admin.service.AdminRestaurantService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/restaurants")
public class AdminRestaurantController {

    private final AdminRestaurantService adminRestaurantService;

    public AdminRestaurantController(AdminRestaurantService adminRestaurantService) {
        this.adminRestaurantService = adminRestaurantService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminRestaurantResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ApiResponse.success(adminRestaurantService.search(keyword, status, page, size));
    }

    @PatchMapping("/{restaurantId}")
    public ApiResponse<Void> update(
            @PathVariable Long restaurantId,
            @Valid @RequestBody AdminRestaurantUpdateRequest request
    ) {
        adminRestaurantService.update(restaurantId, request);
        return ApiResponse.success("음식점 정보를 수정했습니다.", null);
    }
}
