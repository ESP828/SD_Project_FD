package com.example.backend.admin.controller;

import com.example.backend.admin.dto.request.AdminPresetRestaurantOrderRequest;
import com.example.backend.admin.dto.request.AdminPresetRestaurantRequest;
import com.example.backend.admin.dto.request.AdminPresetTagRequest;
import com.example.backend.admin.dto.request.AdminPresetUpsertRequest;
import com.example.backend.admin.dto.response.AdminPresetResponse;
import com.example.backend.admin.service.AdminPresetService;
import com.example.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/presets")
public class AdminPresetController {

    private final AdminPresetService adminPresetService;

    public AdminPresetController(AdminPresetService adminPresetService) {
        this.adminPresetService = adminPresetService;
    }

    @GetMapping
    public ApiResponse<List<AdminPresetResponse>> getAll() {
        return ApiResponse.success(adminPresetService.getAll());
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody AdminPresetUpsertRequest request) {
        return ApiResponse.success("Presset을 등록했습니다.", adminPresetService.create(request));
    }

    @PutMapping("/{presetId}")
    public ApiResponse<Void> update(
            @PathVariable @Positive Long presetId,
            @Valid @RequestBody AdminPresetUpsertRequest request
    ) {
        adminPresetService.update(presetId, request);
        return ApiResponse.success("Presset을 수정했습니다.", null);
    }

    @DeleteMapping("/{presetId}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long presetId) {
        adminPresetService.delete(presetId);
        return ApiResponse.success("Presset을 삭제했습니다.", null);
    }

    @PostMapping("/{presetId}/restaurants")
    public ApiResponse<Void> saveRestaurant(
            @PathVariable @Positive Long presetId,
            @Valid @RequestBody AdminPresetRestaurantRequest request
    ) {
        adminPresetService.saveRestaurant(presetId, request);
        return ApiResponse.success("Presset 음식점을 저장했습니다.", null);
    }

    @DeleteMapping("/{presetId}/restaurants/{restaurantId}")
    public ApiResponse<Void> removeRestaurant(
            @PathVariable @Positive Long presetId,
            @PathVariable @Positive Long restaurantId
    ) {
        adminPresetService.removeRestaurant(presetId, restaurantId);
        return ApiResponse.success("Presset에서 음식점을 제외했습니다.", null);
    }

    @PutMapping("/{presetId}/restaurants/order")
    public ApiResponse<Void> reorderRestaurants(
            @PathVariable @Positive Long presetId,
            @Valid @RequestBody AdminPresetRestaurantOrderRequest request
    ) {
        adminPresetService.reorderRestaurants(presetId, request);
        return ApiResponse.success("음식점 노출 순서를 변경했습니다.", null);
    }

    @PostMapping("/{presetId}/tags")
    public ApiResponse<Void> saveTag(
            @PathVariable @Positive Long presetId,
            @Valid @RequestBody AdminPresetTagRequest request
    ) {
        adminPresetService.saveTag(presetId, request);
        return ApiResponse.success("Presset 태그를 저장했습니다.", null);
    }

    @DeleteMapping("/{presetId}/tags/{tagId}")
    public ApiResponse<Void> removeTag(
            @PathVariable @Positive Long presetId,
            @PathVariable @Positive Integer tagId
    ) {
        adminPresetService.removeTag(presetId, tagId);
        return ApiResponse.success("Presset 태그를 제거했습니다.", null);
    }
}
