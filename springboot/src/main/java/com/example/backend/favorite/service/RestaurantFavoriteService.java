package com.example.backend.favorite.service;

import com.example.backend.favorite.dto.response.RestaurantFavoriteStateResponse;
import com.example.backend.favorite.query.RestaurantFavoriteQueryRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

@Service
public class RestaurantFavoriteService {

    private final RestaurantFavoriteQueryRepository queryRepository;

    public RestaurantFavoriteService(RestaurantFavoriteQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional
    public RestaurantFavoriteStateResponse add(Long restaurantId, Long accountId) {
        validate(restaurantId, accountId);
        if (!queryRepository.exists(accountId, restaurantId)) {
            try {
                queryRepository.add(accountId, restaurantId);
            } catch (DuplicateKeyException ignored) {
                // 복합 PK가 동시 중복 요청도 최종적으로 한 건만 유지한다.
            }
        }
        return new RestaurantFavoriteStateResponse(queryRepository.count(restaurantId), true);
    }

    @Transactional
    public RestaurantFavoriteStateResponse remove(Long restaurantId, Long accountId) {
        validate(restaurantId, accountId);
        queryRepository.remove(accountId, restaurantId);
        return new RestaurantFavoriteStateResponse(queryRepository.count(restaurantId), false);
    }

    private void validate(Long restaurantId, Long accountId) {
        if (restaurantId == null || restaurantId <= 0 || accountId == null || accountId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!queryRepository.activeRestaurantExists(restaurantId)) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
    }
}
