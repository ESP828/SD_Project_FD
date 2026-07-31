package com.example.backend.restaurant.controller;

import com.example.backend.restaurant.dto.request.RestaurantCreateRequest;
import com.example.backend.restaurant.dto.response.RestaurantResponse;
import com.example.backend.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantResponse createRestaurant(
            @RequestParam Long accountId,
            @Valid @RequestBody RestaurantCreateRequest request
    ) {

        return restaurantService.createRestaurant(accountId, request);
    }

}
