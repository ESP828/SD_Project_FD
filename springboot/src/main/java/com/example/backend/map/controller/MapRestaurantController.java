package com.example.backend.map.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.map.dto.response.PublicRestaurantMarkerResponse;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 지도 화면에 표시할 음식점 마커를 공공데이터 기반 자체 DB에서 조회한다(카카오 장소검색 미사용).
 */
@RestController
@RequestMapping("/api/public/map")
public class MapRestaurantController {

    private static final int MAX_RESULTS = 300;

    private final PublicRestaurantRepository publicRestaurantRepository;

    public MapRestaurantController(PublicRestaurantRepository publicRestaurantRepository) {
        this.publicRestaurantRepository = publicRestaurantRepository;
    }

    @GetMapping("/restaurants")
    public ApiResponse<List<PublicRestaurantMarkerResponse>> searchInBounds(
            @RequestParam BigDecimal swLat,
            @RequestParam BigDecimal swLng,
            @RequestParam BigDecimal neLat,
            @RequestParam BigDecimal neLng,
            @RequestParam(required = false) String keyword
    ) {
        Pageable limit = PageRequest.of(0, MAX_RESULTS);
        List<PublicRestaurant> restaurants = StringUtils.hasText(keyword)
                ? publicRestaurantRepository.findByLatitudeBetweenAndLongitudeBetweenAndNameContaining(
                        swLat, neLat, swLng, neLng, keyword.trim(), limit
                )
                : publicRestaurantRepository.findByLatitudeBetweenAndLongitudeBetween(
                        swLat, neLat, swLng, neLng, limit
                );
        List<PublicRestaurantMarkerResponse> response = restaurants.stream()
                .map(PublicRestaurantMarkerResponse::from)
                .toList();
        return ApiResponse.success(response);
    }
}
