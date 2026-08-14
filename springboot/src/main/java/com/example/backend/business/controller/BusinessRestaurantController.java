package com.example.backend.business.controller;

import com.example.backend.business.dto.request.BusinessRestaurantStatusUpdateRequest;
import com.example.backend.business.dto.response.BusinessRestaurantResponse;
import com.example.backend.business.service.BusinessRestaurantService;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.restaurant.dto.request.RestaurantCreateRequest;
import com.example.backend.restaurant.dto.request.RestaurantUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/restaurants")
public class BusinessRestaurantController {

    private final BusinessRestaurantService businessRestaurantService;

    public BusinessRestaurantController(BusinessRestaurantService businessRestaurantService) {
        this.businessRestaurantService = businessRestaurantService;
    }

    @GetMapping
    public ApiResponse<List<BusinessRestaurantResponse>> getMyRestaurants(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(businessRestaurantService.findMyRestaurants(account.accountId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusinessRestaurantResponse> create(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody RestaurantCreateRequest request
    ) {
        return ApiResponse.success(
                "음식점이 등록되었습니다.",
                businessRestaurantService.create(account.accountId(), request)
        );
    }

    @GetMapping("/{restaurantId}")
    public ApiResponse<BusinessRestaurantResponse> getMyRestaurant(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long restaurantId
    ) {
        return ApiResponse.success(
                businessRestaurantService.findMyRestaurant(account.accountId(), restaurantId)
        );
    }

    @PutMapping("/{restaurantId}")
    public ApiResponse<BusinessRestaurantResponse> update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long restaurantId,
            @Valid @RequestBody RestaurantUpdateRequest request
    ) {
        return ApiResponse.success(
                "음식점 정보가 수정되었습니다.",
                businessRestaurantService.update(account.accountId(), restaurantId, request)
        );
    }

    @PatchMapping("/{restaurantId}/status")
    public ApiResponse<BusinessRestaurantResponse> changeStatus(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long restaurantId,
            @Valid @RequestBody BusinessRestaurantStatusUpdateRequest request
    ) {
        return ApiResponse.success(
                "음식점 운영 상태가 변경되었습니다.",
                businessRestaurantService.changeStatus(account.accountId(), restaurantId, request)
        );
    }

    @DeleteMapping("/{restaurantId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long restaurantId
    ) {
        businessRestaurantService.delete(account.accountId(), restaurantId);
        return ApiResponse.success("음식점이 삭제되었습니다.", null);
    }
}
