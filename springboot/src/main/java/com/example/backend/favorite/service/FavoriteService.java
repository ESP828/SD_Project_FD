package com.example.backend.favorite.service;

import com.example.backend.favorite.domain.entity.Favorite;
import com.example.backend.favorite.domain.entity.FavoriteId;
import com.example.backend.favorite.repository.FavoriteRepository;
import com.example.backend.restaurant.exception.RestaurantNotFoundException;
import com.example.backend.restaurant.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final RestaurantRepository restaurantRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, RestaurantRepository restaurantRepository) {
        this.favoriteRepository = favoriteRepository;
        this.restaurantRepository = restaurantRepository;
    }

    /**
     * 찜 상태를 토글한다. 처리 후의 찜 여부(true=찜함)를 반환한다.
     */
    @Transactional
    public boolean toggle(Long accountId, Long restaurantId) {
        restaurantRepository.findById(restaurantId)
                .filter(restaurant -> restaurant.isActive())
                .orElseThrow(RestaurantNotFoundException::new);
        FavoriteId id = new FavoriteId(accountId, restaurantId);
        if (favoriteRepository.existsById(id)) {
            favoriteRepository.deleteById(id);
            return false;
        }
        favoriteRepository.save(new Favorite(accountId, restaurantId));
        return true;
    }
}
