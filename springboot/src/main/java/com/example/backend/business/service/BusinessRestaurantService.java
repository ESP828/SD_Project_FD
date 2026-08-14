package com.example.backend.business.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.business.dto.request.BusinessRestaurantStatusUpdateRequest;
import com.example.backend.business.dto.response.BusinessRestaurantResponse;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.domain.entity.RestaurantCategory;
import com.example.backend.restaurant.domain.type.RestaurantStatus;
import com.example.backend.restaurant.dto.request.RestaurantCreateRequest;
import com.example.backend.restaurant.dto.request.RestaurantUpdateRequest;
import com.example.backend.restaurant.exception.CategoryNotFoundException;
import com.example.backend.restaurant.exception.DuplicateRestaurantException;
import com.example.backend.restaurant.exception.OwnerNotFoundException;
import com.example.backend.restaurant.exception.RestaurantNotFoundException;
import com.example.backend.restaurant.repository.RestaurantCategoryRepository;
import com.example.backend.restaurant.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BusinessRestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantCategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public BusinessRestaurantService(
            RestaurantRepository restaurantRepository,
            RestaurantCategoryRepository categoryRepository,
            AccountRepository accountRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<BusinessRestaurantResponse> findMyRestaurants(Long accountId) {
        requireActiveOwner(accountId);
        return restaurantRepository
                .findAllByOwnerAccountIdAndStatusNotOrderByCreatedAtDescRestaurantIdDesc(
                        accountId,
                        RestaurantStatus.DELETED
                )
                .stream()
                .map(BusinessRestaurantResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessRestaurantResponse findMyRestaurant(Long accountId, Long restaurantId) {
        requireActiveOwner(accountId);
        return BusinessRestaurantResponse.from(requireOwnedRestaurant(accountId, restaurantId));
    }

    @Transactional
    public BusinessRestaurantResponse create(Long accountId, RestaurantCreateRequest request) {
        Account owner = requireActiveOwnerForCreate(accountId);
        RestaurantCategory category = requireActiveCategory(request.getCategoryId());
        String name = normalizeRequired(request.getName());
        String address = normalizeRequired(request.getAddress());
        if (restaurantRepository.existsByOwnerAccountIdAndNameAndAddressAndStatusNot(
                accountId,
                name,
                address,
                RestaurantStatus.DELETED
        )) {
            throw new DuplicateRestaurantException();
        }

        Restaurant restaurant = Restaurant.create(
                owner,
                category,
                name,
                address,
                normalizeOptional(request.getAddressDetail()),
                decimal(request.getLatitude()),
                decimal(request.getLongitude()),
                normalizeOptional(request.getPhone()),
                normalizeOptional(request.getOpeningHours()),
                normalizeOptional(request.getClosedDays()),
                normalizeOptional(request.getDescription())
        );
        return BusinessRestaurantResponse.from(restaurantRepository.save(restaurant));
    }

    @Transactional
    public BusinessRestaurantResponse update(
            Long accountId,
            Long restaurantId,
            RestaurantUpdateRequest request
    ) {
        requireActiveOwner(accountId);
        Restaurant restaurant = requireOwnedRestaurant(accountId, restaurantId);
        RestaurantCategory category = requireActiveCategory(request.getCategoryId());
        String name = normalizeRequired(request.getName());
        String address = normalizeRequired(request.getAddress());
        if (restaurantRepository
                .existsByOwnerAccountIdAndNameAndAddressAndStatusNotAndRestaurantIdNot(
                        accountId,
                        name,
                        address,
                        RestaurantStatus.DELETED,
                        restaurantId
                )) {
            throw new DuplicateRestaurantException();
        }

        restaurant.update(
                category,
                name,
                address,
                normalizeOptional(request.getAddressDetail()),
                decimal(request.getLatitude()),
                decimal(request.getLongitude()),
                normalizeOptional(request.getPhone()),
                normalizeOptional(request.getOpeningHours()),
                normalizeOptional(request.getClosedDays()),
                normalizeOptional(request.getDescription())
        );
        return BusinessRestaurantResponse.from(restaurant);
    }

    @Transactional
    public BusinessRestaurantResponse changeStatus(
            Long accountId,
            Long restaurantId,
            BusinessRestaurantStatusUpdateRequest request
    ) {
        requireActiveOwner(accountId);
        Restaurant restaurant = requireOwnedRestaurant(accountId, restaurantId);
        if (request.status() == RestaurantStatus.ACTIVE) {
            restaurant.activate();
        } else if (request.status() == RestaurantStatus.INACTIVE) {
            restaurant.deactivate();
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return BusinessRestaurantResponse.from(restaurant);
    }

    @Transactional
    public void delete(Long accountId, Long restaurantId) {
        requireActiveOwner(accountId);
        requireOwnedRestaurant(accountId, restaurantId).delete();
    }

    private Account requireActiveOwner(Long accountId) {
        return accountRepository.findById(accountId)
                .filter(Account::isActive)
                .orElseThrow(OwnerNotFoundException::new);
    }

    private Account requireActiveOwnerForCreate(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .filter(Account::isActive)
                .orElseThrow(OwnerNotFoundException::new);
    }

    private Restaurant requireOwnedRestaurant(Long accountId, Long restaurantId) {
        return restaurantRepository.findByRestaurantIdAndOwnerAccountIdAndStatusNot(
                        restaurantId,
                        accountId,
                        RestaurantStatus.DELETED
                )
                .orElseThrow(RestaurantNotFoundException::new);
    }

    private RestaurantCategory requireActiveCategory(Integer categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByCategoryIdAndActiveTrue(categoryId)
                .orElseThrow(CategoryNotFoundException::new);
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
