package com.example.backend.restaurant.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.restaurant.dto.response.RestaurantCategoryResponse;
import com.example.backend.restaurant.service.RestaurantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/restaurant-categories")
public class RestaurantCategoryController {

    private final RestaurantService restaurantService;

    public RestaurantCategoryController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping
    public ApiResponse<List<RestaurantCategoryResponse>> getCategories() {
        return ApiResponse.success(restaurantService.getActiveCategories());
    }
}
