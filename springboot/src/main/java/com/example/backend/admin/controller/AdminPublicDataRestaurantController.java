package com.example.backend.admin.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.restaurant.integration.publicdata.PublicDataRestaurantSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공공데이터(상가업소정보) 음식점 데이터 적재를 관리자가 수동으로 실행한다.
 *
 * <p>공공데이터 API는 일일 호출(트래픽) 제한이 있어 전국 데이터를 한 번에 받을 수 없다.
 * startPage/maxPages를 나눠 여러 번 호출해 이어받는 것을 전제로 한다.</p>
 */
@RestController
@RequestMapping("/api/admin/restaurants/public-data")
public class AdminPublicDataRestaurantController {

    private final PublicDataRestaurantSyncService syncService;

    public AdminPublicDataRestaurantController(PublicDataRestaurantSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ApiResponse<PublicDataRestaurantSyncService.SyncResult> sync(
            @RequestParam(defaultValue = "1") int startPage,
            @RequestParam(defaultValue = "500") int maxPages
    ) {
        PublicDataRestaurantSyncService.SyncResult result = syncService.sync(startPage, maxPages);
        return ApiResponse.success("공공데이터 음식점 동기화를 실행했습니다.", result);
    }
}
