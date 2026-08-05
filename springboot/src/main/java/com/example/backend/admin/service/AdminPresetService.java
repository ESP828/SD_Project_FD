package com.example.backend.admin.service;

import com.example.backend.admin.dto.request.AdminPresetRestaurantOrderRequest;
import com.example.backend.admin.dto.request.AdminPresetRestaurantRequest;
import com.example.backend.admin.dto.request.AdminPresetTagRequest;
import com.example.backend.admin.dto.request.AdminPresetUpsertRequest;
import com.example.backend.admin.dto.response.AdminPresetResponse;
import com.example.backend.admin.query.AdminPresetQueryRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminPresetService {

    private final AdminPresetQueryRepository queryRepository;

    public AdminPresetService(AdminPresetQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminPresetResponse> getAll() {
        return queryRepository.findAll();
    }

    @Transactional
    public Long create(AdminPresetUpsertRequest request) {
        Long presetId = queryRepository.create(request);
        if (presetId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return presetId;
    }

    @Transactional
    public void update(Long presetId, AdminPresetUpsertRequest request) {
        requirePreset(presetId);
        queryRepository.update(presetId, request);
    }

    @Transactional
    public void delete(Long presetId) {
        requirePreset(presetId);
        queryRepository.logicalDelete(presetId);
    }

    @Transactional
    public void saveRestaurant(Long presetId, AdminPresetRestaurantRequest request) {
        requirePreset(presetId);
        if (!queryRepository.activeRestaurantExists(request.restaurantId())) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        queryRepository.saveRestaurant(presetId, request);
    }

    @Transactional
    public void removeRestaurant(Long presetId, Long restaurantId) {
        requirePreset(presetId);
        queryRepository.removeRestaurant(presetId, restaurantId);
    }

    @Transactional
    public void reorderRestaurants(Long presetId, AdminPresetRestaurantOrderRequest request) {
        requirePreset(presetId);
        Set<Long> restaurantIds = new HashSet<>();
        for (AdminPresetRestaurantOrderRequest.Item item : request.restaurants()) {
            if (!restaurantIds.add(item.restaurantId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 음식점을 중복 지정할 수 없습니다.");
            }
            int updated = queryRepository.updateRestaurantOrder(
                    presetId,
                    item.restaurantId(),
                    item.displayOrder() == null ? 0 : item.displayOrder()
            );
            if (updated == 0) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Presset에 연결되지 않은 음식점이 포함되어 있습니다."
                );
            }
        }
    }

    @Transactional
    public void saveTag(Long presetId, AdminPresetTagRequest request) {
        requirePreset(presetId);
        if (!queryRepository.tagExists(request.tagId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "태그를 찾을 수 없습니다.");
        }
        queryRepository.saveTag(presetId, request);
    }

    @Transactional
    public void removeTag(Long presetId, Integer tagId) {
        requirePreset(presetId);
        queryRepository.removeTag(presetId, tagId);
    }

    private void requirePreset(Long presetId) {
        if (presetId == null || presetId <= 0 || !queryRepository.presetExists(presetId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Presset을 찾을 수 없습니다.");
        }
    }
}
