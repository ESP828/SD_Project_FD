package com.example.backend.favorite.service;

import com.example.backend.favorite.dto.response.RestaurantFavoriteStateResponse;
import com.example.backend.favorite.query.PublicRestaurantFavoriteQueryRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공공데이터 출처 음식점(public_restaurant)에 대한 찜하기 등록·해제.
 * {@link RestaurantFavoriteService}의 공공데이터 버전.
 */
@Service
public class PublicRestaurantFavoriteService {

    private final PublicRestaurantFavoriteQueryRepository queryRepository;

    public PublicRestaurantFavoriteService(PublicRestaurantFavoriteQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional
    public RestaurantFavoriteStateResponse add(Long publicRestaurantId, Long accountId) {
        validate(publicRestaurantId, accountId);
        if (!queryRepository.exists(accountId, publicRestaurantId)) {
            try {
                queryRepository.add(accountId, publicRestaurantId);
            } catch (DuplicateKeyException ignored) {
                // 유니크 제약이 동시 중복 요청도 최종적으로 한 건만 유지한다.
            }
        }
        return new RestaurantFavoriteStateResponse(queryRepository.count(publicRestaurantId), true);
    }

    @Transactional
    public RestaurantFavoriteStateResponse remove(Long publicRestaurantId, Long accountId) {
        validate(publicRestaurantId, accountId);
        queryRepository.remove(accountId, publicRestaurantId);
        return new RestaurantFavoriteStateResponse(queryRepository.count(publicRestaurantId), false);
    }

    public boolean isFavorited(Long publicRestaurantId, Long accountId) {
        if (publicRestaurantId == null || accountId == null) return false;
        return queryRepository.exists(accountId, publicRestaurantId);
    }

    public long count(Long publicRestaurantId) {
        return queryRepository.count(publicRestaurantId);
    }

    private void validate(Long publicRestaurantId, Long accountId) {
        if (publicRestaurantId == null || publicRestaurantId <= 0 || accountId == null || accountId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!queryRepository.publicRestaurantExists(publicRestaurantId)) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
    }
}
