package com.example.backend.preset.service;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.preset.dto.request.PresetCreateRequest;
import com.example.backend.preset.dto.response.FavoriteStateResponse;
import com.example.backend.preset.dto.response.PresetDetailResponse;
import com.example.backend.preset.dto.response.PresetMapResponse;
import com.example.backend.preset.dto.response.PresetPageResponse;
import com.example.backend.preset.dto.response.PresetRestaurantResponse;
import com.example.backend.preset.dto.response.PresetSummaryResponse;
import com.example.backend.preset.dto.response.PresetTagResponse;
import com.example.backend.preset.exception.PresetNotFoundException;
import com.example.backend.preset.query.PresetQueryRepository;
import com.example.backend.preset.query.PresetQueryRepository.PresetDetailRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;

@Service
public class PresetService {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 24;
    private static final Set<String> SORT_OPTIONS = Set.of("popular", "latest", "favorite");

    private final PresetQueryRepository queryRepository;

    public PresetService(PresetQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public PresetPageResponse getPresets(
            Long accountId,
            Integer page,
            Integer size,
            String sort,
            Integer tagId,
            String keyword
    ) {
        int normalizedPage = page == null ? 0 : page;
        int normalizedSize = size == null ? DEFAULT_PAGE_SIZE : size;
        String normalizedSort = sort == null || sort.isBlank() ? "popular" : sort.trim().toLowerCase();
        validateListRequest(normalizedPage, normalizedSize, normalizedSort, tagId, keyword);

        long totalElements = queryRepository.countActive(accountId, tagId, keyword);
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / normalizedSize);
        List<PresetSummaryResponse> content = normalizedPage >= totalPages && totalElements > 0
                ? List.of()
                : queryRepository.findActivePage(
                        accountId,
                        tagId,
                        keyword,
                        normalizedSort,
                        normalizedPage * normalizedSize,
                        normalizedSize
                );

        return new PresetPageResponse(
                content,
                normalizedPage,
                normalizedSize,
                totalElements,
                totalPages,
                normalizedPage == 0,
                totalPages == 0 || normalizedPage >= totalPages - 1
        );
    }

    @Transactional
    public Long createPreset(Long accountId, PresetCreateRequest request) {
        validateId(accountId, "계정");
        Long presetId = queryRepository.create(accountId, request);
        if (presetId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return presetId;
    }

    @Transactional(readOnly = true)
    public List<PresetTagResponse> getFilterTags() {
        return queryRepository.findActiveFilterTags();
    }

    @Transactional
    public PresetDetailResponse getPreset(Long presetId, Long accountId) {
        validateId(presetId, "프리셋");
        if (queryRepository.incrementViewCount(presetId, accountId) == 0) {
            throw new PresetNotFoundException(presetId);
        }
        return toDetail(loadPreset(presetId, accountId), accountId);
    }

    @Transactional(readOnly = true)
    public PresetMapResponse getMapPreset(Long presetId, Long accountId) {
        validateId(presetId, "프리셋");
        PresetDetailRow preset = loadPreset(presetId, accountId);
        return new PresetMapResponse(
                preset.presetId(),
                preset.title(),
                preset.summary(),
                preset.imageUrl(),
                preset.favoriteCount(),
                preset.favoriteByCurrentUser(),
                queryRepository.findActiveRestaurants(presetId, accountId)
        );
    }

    @Transactional
    public FavoriteStateResponse addFavorite(Long presetId, Long accountId) {
        validateFavoriteRequest(presetId, accountId);
        if (!queryRepository.favoriteExists(accountId, presetId)) {
            try {
                queryRepository.addFavorite(accountId, presetId);
            } catch (DuplicateKeyException ignored) {
                // 복합 PK가 동시 중복 요청도 최종적으로 한 건만 유지한다.
            }
        }
        return new FavoriteStateResponse(queryRepository.countFavorites(presetId), true);
    }

    @Transactional
    public FavoriteStateResponse removeFavorite(Long presetId, Long accountId) {
        validateFavoriteRequest(presetId, accountId);
        queryRepository.removeFavorite(accountId, presetId);
        return new FavoriteStateResponse(queryRepository.countFavorites(presetId), false);
    }

    @Transactional(readOnly = true)
    public List<PresetSummaryResponse> getSavedPresets(Long accountId) {
        validateId(accountId, "계정");
        return queryRepository.findSavedByAccount(accountId);
    }

    private PresetDetailResponse toDetail(PresetDetailRow preset, Long accountId) {
        List<PresetRestaurantResponse> restaurants =
                queryRepository.findActiveRestaurants(preset.presetId(), accountId);
        return new PresetDetailResponse(
                preset.presetId(),
                preset.title(),
                preset.summary(),
                preset.description(),
                preset.imageUrl(),
                preset.category(),
                preset.viewCount(),
                preset.favoriteCount(),
                preset.favoriteByCurrentUser(),
                preset.createdAt(),
                queryRepository.findTagsByPresetId(preset.presetId()),
                restaurants
        );
    }

    private PresetDetailRow loadPreset(Long presetId, Long accountId) {
        return queryRepository.findActiveById(presetId, accountId)
                .orElseThrow(() -> new PresetNotFoundException(presetId));
    }

    private void validateFavoriteRequest(Long presetId, Long accountId) {
        validateId(presetId, "프리셋");
        validateId(accountId, "계정");
        if (!queryRepository.activePresetExists(presetId, accountId)) {
            throw new PresetNotFoundException(presetId);
        }
    }

    private static void validateListRequest(
            int page,
            int size,
            String sort,
            Integer tagId,
            String keyword
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지 크기는 1~24 사이여야 합니다.");
        }
        if (!SORT_OPTIONS.contains(sort)) {
            throw new IllegalArgumentException("정렬 값은 popular, latest, favorite 중 하나여야 합니다.");
        }
        if (tagId != null && tagId <= 0) {
            throw new IllegalArgumentException("태그 번호는 1 이상이어야 합니다.");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("검색어는 100자 이하여야 합니다.");
        }
    }

    private static void validateId(Long id, String label) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(label + " 번호는 1 이상이어야 합니다.");
        }
    }
}
