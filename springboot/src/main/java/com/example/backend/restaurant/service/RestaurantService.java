package com.example.backend.restaurant.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.favorite.domain.entity.FavoriteId;
import com.example.backend.favorite.repository.FavoriteRepository;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.domain.entity.RestaurantCategory;
import com.example.backend.restaurant.dto.request.RestaurantCreateRequest;
import com.example.backend.restaurant.dto.response.MenuResponse;
import com.example.backend.restaurant.dto.response.RestaurantCategoryResponse;
import com.example.backend.restaurant.dto.response.RestaurantDetailResponse;
import com.example.backend.restaurant.dto.response.RestaurantResponse;
import com.example.backend.restaurant.exception.CategoryNotFoundException;
import com.example.backend.restaurant.exception.DuplicateRestaurantException;
import com.example.backend.restaurant.exception.OwnerNotFoundException;
import com.example.backend.restaurant.exception.RestaurantNotFoundException;
import com.example.backend.restaurant.mapper.RestaurantMapper;
import com.example.backend.restaurant.repository.MenuRepository;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import com.example.backend.restaurant.repository.RestaurantCategoryRepository;
import com.example.backend.restaurant.repository.RestaurantRepository;
import com.example.backend.review.repository.ReviewRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantCategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final MenuRepository menuRepository;
    private final ReviewRepository reviewRepository;
    private final FavoriteRepository favoriteRepository;
    private final PublicRestaurantRepository publicRestaurantRepository;

    public RestaurantService(
            RestaurantRepository restaurantRepository,
            RestaurantCategoryRepository categoryRepository,
            AccountRepository accountRepository,
            MenuRepository menuRepository,
            ReviewRepository reviewRepository,
            FavoriteRepository favoriteRepository,
            PublicRestaurantRepository publicRestaurantRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.menuRepository = menuRepository;
        this.reviewRepository = reviewRepository;
        this.favoriteRepository = favoriteRepository;
        this.publicRestaurantRepository = publicRestaurantRepository;
    }

    /**
     * DB 수정 없이 Java 메모리 상에서 가중치 점수 계산 및 컷오프
     */
    public List<PublicRestaurant> searchPublicRestaurantsByBounds(
            BigDecimal latitude,
            BigDecimal longitude,
            String keyword,
            Integer limit
    ) {
        BigDecimal minLat = latitude.subtract(new BigDecimal("0.04"));
        BigDecimal maxLat = latitude.add(new BigDecimal("0.04"));
        BigDecimal minLng = longitude.subtract(new BigDecimal("0.04"));
        BigDecimal maxLng = longitude.add(new BigDecimal("0.04"));

        List<PublicRestaurant> candidates = publicRestaurantRepository.findByLatitudeBetweenAndLongitudeBetween(
                minLat, maxLat, minLng, maxLng, Pageable.unpaged()
        );

        if (keyword == null || keyword.trim().isEmpty()) {
            return candidates;
        }

        String cleanKeyword = keyword.trim().toLowerCase();
        Set<String> tokens = Arrays.stream(cleanKeyword.split("\\s+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());

        double minScoreThreshold = 0.30;
        int maxLimit = (limit != null && limit > 0) ? limit : 20;

        record ScoredRestaurant(PublicRestaurant restaurant, double score) {}

        return candidates.stream()
                .map(r -> new ScoredRestaurant(r, calculateMatchScore(r, tokens, cleanKeyword)))
                .filter(sr -> sr.score() >= minScoreThreshold)
                .sorted(Comparator.comparingDouble(ScoredRestaurant::score).reversed())
                .limit(maxLimit)
                .map(ScoredRestaurant::restaurant)
                .toList();
    }

    private double calculateMatchScore(PublicRestaurant r, Set<String> tokens, String fullKeyword) {
        double score = 0.0;

        String name = r.getName() != null ? r.getName().toLowerCase() : "";
        String catLarge = r.getCategoryLargeName() != null ? r.getCategoryLargeName().toLowerCase() : "";
        String catSmall = r.getCategorySmallName() != null ? r.getCategorySmallName().toLowerCase() : "";
        String roadAddr = r.getRoadAddress() != null ? r.getRoadAddress().toLowerCase() : "";
        String lotAddr = r.getLotAddress() != null ? r.getLotAddress().toLowerCase() : "";

        if (name.contains(fullKeyword)) score += 0.6;
        if (catLarge.contains(fullKeyword) || catSmall.contains(fullKeyword)) score += 0.4;
        if (roadAddr.contains(fullKeyword) || lotAddr.contains(fullKeyword)) score += 0.2;

        for (String token : tokens) {
            if (name.contains(token)) score += 0.3;
            if (catLarge.contains(token) || catSmall.contains(token)) score += 0.2;
            if (roadAddr.contains(token) || lotAddr.contains(token)) score += 0.1;
        }

        return score;
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
