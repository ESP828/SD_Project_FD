package com.example.backend.restaurant.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.favorite.domain.entity.FavoriteId;
import com.example.backend.favorite.repository.FavoriteRepository;
import com.example.backend.restaurant.domain.entity.*;
import com.example.backend.restaurant.dto.request.RestaurantCreateRequest;
import com.example.backend.restaurant.dto.response.MenuResponse;
import com.example.backend.restaurant.dto.response.RestaurantDetailResponse;
import com.example.backend.restaurant.dto.response.RestaurantCategoryResponse;
import com.example.backend.restaurant.dto.response.RestaurantResponse;
import com.example.backend.restaurant.exception.*;
import com.example.backend.restaurant.mapper.RestaurantMapper;
import com.example.backend.restaurant.repository.MenuRepository;
import com.example.backend.restaurant.repository.RestaurantCategoryRepository;
import com.example.backend.restaurant.repository.RestaurantRepository;
import com.example.backend.review.repository.ReviewRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantCategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final MenuRepository menuRepository;
    private final ReviewRepository reviewRepository;
    private final FavoriteRepository favoriteRepository;

    public RestaurantService(
            RestaurantRepository restaurantRepository,
            RestaurantCategoryRepository categoryRepository,
            AccountRepository accountRepository,
            MenuRepository menuRepository,
            ReviewRepository reviewRepository,
            FavoriteRepository favoriteRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.menuRepository = menuRepository;
        this.reviewRepository = reviewRepository;
        this.favoriteRepository = favoriteRepository;
    }

    public RestaurantResponse createRestaurant(
            Long accountId,
            RestaurantCreateRequest request
    ) {

        Account owner = accountRepository.findById(accountId)
                .orElseThrow(OwnerNotFoundException::new);

        RestaurantCategory category = null;

        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(CategoryNotFoundException::new);
        }
        if (restaurantRepository.existsByName(request.getName())) {
    throw new DuplicateRestaurantException();
}
        Restaurant restaurant = Restaurant.create(
                owner,
                category,
                request.getName(),
                request.getAddress(),
                request.getAddressDetail(),
                request.getLatitude() != null
                        ? BigDecimal.valueOf(request.getLatitude())
                        : null,
                request.getLongitude() != null
                        ? BigDecimal.valueOf(request.getLongitude())
                        : null,
                request.getPhone(),
                request.getOpeningHours(),
                request.getClosedDays(),
                request.getDescription()
        );

        restaurantRepository.save(restaurant);

        return RestaurantMapper.toResponse(restaurant);
    }

    public RestaurantDetailResponse getDetail(Long restaurantId, Long viewerAccountId) {
        Restaurant restaurant = requireReadableRestaurant(restaurantId, viewerAccountId);

        Double averageRating = reviewRepository.averageRatingByRestaurantId(restaurantId);
        long reviewCount = reviewRepository.countActiveByRestaurantId(restaurantId);
        long favoriteCount = favoriteRepository.countByRestaurantId(restaurantId);
        long menuCount = menuRepository.countVisibleByRestaurantId(restaurantId);
        boolean favoritedByMe = viewerAccountId != null
                && favoriteRepository.existsById(new FavoriteId(viewerAccountId, restaurantId));
        boolean isOwner = viewerAccountId != null
                && restaurant.getOwner().getAccountId().equals(viewerAccountId);

        return RestaurantDetailResponse.of(
                restaurant, averageRating, reviewCount, favoriteCount, menuCount, favoritedByMe, isOwner
        );
    }

    public List<MenuResponse> getMenu(Long restaurantId, Long viewerAccountId) {
        requireReadableRestaurant(restaurantId, viewerAccountId);
        return menuRepository.findVisibleByRestaurantId(restaurantId).stream()
                .map(MenuResponse::from)
                .toList();
    }

    public List<RestaurantCategoryResponse> getActiveCategories() {
        return categoryRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(RestaurantCategoryResponse::from)
                .toList();
    }

    private Restaurant requireReadableRestaurant(Long restaurantId, Long viewerAccountId) {
        return restaurantRepository.findById(restaurantId)
                .filter(restaurant -> restaurant.isReadableBy(viewerAccountId))
                .orElseThrow(RestaurantNotFoundException::new);
    }

}
